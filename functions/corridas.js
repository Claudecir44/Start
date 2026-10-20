// Ciclo da corrida: estimar, pedir, aceitar, embarcar, finalizar, cancelar.
// Preço, aceite e mudança de situação acontecem SÓ aqui (Admin SDK):
// firestore.rules não deixa o app criar corrida nem mudar status/valor.
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');

const MAPS_SERVER_KEY = process.env.MAPS_SERVER_KEY || '';

// Precisa bater com Tarifa.PADRAO no app.
const TARIFA_PADRAO = { bandeirada: 4.0, porKm: 1.8, porMinuto: 0.3, minimo: 8.0, raioBuscaKm: 10.0 };

// Precisa bater com StatusCorrida no app.
const STATUS = {
    PROCURANDO: 'procurando',
    ACEITA: 'aceita',
    EM_ANDAMENTO: 'em_andamento',
    FINALIZADA: 'finalizada',
    CANCELADA: 'cancelada',
};
const ATIVAS = [STATUS.PROCURANDO, STATUS.ACEITA, STATUS.EM_ANDAMENTO];

function exigirLogin(request) {
    const uid = request.auth && request.auth.uid;
    if (!uid) throw new HttpsError('unauthenticated', 'Entre na sua conta para continuar.');
    return uid;
}

function lerLocal(valor, nome) {
    const lat = Number(valor && valor.lat);
    const lng = Number(valor && valor.lng);
    if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
        throw new HttpsError('invalid-argument', 'Local de ' + nome + ' inválido.');
    }
    const endereco = String((valor && valor.endereco) || '').trim().slice(0, 300);
    return { endereco, lat, lng };
}

async function lerTarifa() {
    const doc = await admin.firestore().collection('config').doc('tarifa').get();
    return { ...TARIFA_PADRAO, ...(doc.exists ? doc.data() : {}) };
}

// Fórmula atual (provisória — o dono vai definir regras novas de cobrança):
// bandeirada + km + minuto, nunca abaixo do mínimo, arredondada pra cima
// nos 50 centavos.
function calcularValor(tarifa, distanciaMetros, duracaoSegundos) {
    const bruto = tarifa.bandeirada
        + tarifa.porKm * (distanciaMetros / 1000)
        + tarifa.porMinuto * (duracaoSegundos / 60);
    return Math.ceil(Math.max(bruto, tarifa.minimo) * 2) / 2;
}

// Rota de carro com trânsito (Google Routes API).
async function calcularRota(origem, destino) {
    if (!MAPS_SERVER_KEY) throw new HttpsError('failed-precondition', 'Mapa não configurado no servidor.');
    const resposta = await fetch('https://routes.googleapis.com/directions/v2:computeRoutes', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Goog-Api-Key': MAPS_SERVER_KEY,
            'X-Goog-FieldMask': 'routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline',
        },
        body: JSON.stringify({
            origin: { location: { latLng: { latitude: origem.lat, longitude: origem.lng } } },
            destination: { location: { latLng: { latitude: destino.lat, longitude: destino.lng } } },
            travelMode: 'DRIVE',
            routingPreference: 'TRAFFIC_AWARE',
            languageCode: 'pt-BR',
            regionCode: 'BR',
        }),
    });
    const corpo = await resposta.json().catch(() => ({}));
    const rota = corpo.routes && corpo.routes[0];
    if (!resposta.ok || !rota) {
        console.warn('Routes API sem rota:', resposta.status, JSON.stringify(corpo).slice(0, 500));
        throw new HttpsError('not-found', 'Não encontramos um caminho de carro entre esses dois pontos.');
    }
    return {
        distanciaMetros: Number(rota.distanceMeters || 0),
        duracaoSegundos: parseInt(String(rota.duration || '0s').replace('s', ''), 10) || 0,
        polyline: (rota.polyline && rota.polyline.encodedPolyline) || '',
    };
}

async function estimar(origem, destino) {
    const [rota, tarifa] = await Promise.all([calcularRota(origem, destino), lerTarifa()]);
    if (rota.distanciaMetros < 200) {
        throw new HttpsError('invalid-argument', 'A partida e o destino estão perto demais.');
    }
    return { ...rota, valor: calcularValor(tarifa, rota.distanciaMetros, rota.duracaoSegundos) };
}

// Preço e rota antes de pedir (o passageiro decide se pede).
exports.estimarCorrida = onCall(async (request) => {
    exigirLogin(request);
    const dados = request.data || {};
    return estimar(lerLocal(dados.origem, 'partida'), lerLocal(dados.destino, 'destino'));
});

// Cria a corrida (status procurando). Recalcula rota e preço aqui — o valor
// mostrado antes no app é só a estimativa.
exports.pedirCorrida = onCall(async (request) => {
    const uid = exigirLogin(request);
    const dados = request.data || {};
    const origem = lerLocal(dados.origem, 'partida');
    const destino = lerLocal(dados.destino, 'destino');
    const db = admin.firestore();

    const perfilRef = db.collection('passageiros').doc(uid);
    const perfil = await perfilRef.get();
    if (!perfil.exists) throw new HttpsError('failed-precondition', 'Complete seu cadastro de passageiro.');
    const p = perfil.data();
    if (p.bloqueado) throw new HttpsError('permission-denied', 'Sua conta está bloqueada.');
    if (p.corridaAtualId) {
        const atual = await db.collection('corridas').doc(p.corridaAtualId).get();
        if (atual.exists && ATIVAS.includes(atual.data().status)) {
            throw new HttpsError('failed-precondition', 'Você já tem uma corrida em andamento.');
        }
    }

    const estimativa = await estimar(origem, destino);
    const corridaRef = db.collection('corridas').doc();
    const lote = db.batch();
    lote.set(corridaRef, {
        status: STATUS.PROCURANDO,
        passageiroId: uid,
        passageiroNome: p.nomeCompleto || '',
        passageiroFotoUrl: p.fotoUrl || null,
        passageiroTelefone: p.telefone || null,
        origem,
        destino,
        distanciaMetros: estimativa.distanciaMetros,
        duracaoSegundos: estimativa.duracaoSegundos,
        polyline: estimativa.polyline,
        valor: estimativa.valor,
        formaPagamento: 'direto',
        criadaEm: admin.firestore.FieldValue.serverTimestamp(),
    });
    lote.update(perfilRef, { corridaAtualId: corridaRef.id });
    await lote.commit();
    return { corridaId: corridaRef.id, valor: estimativa.valor };
});

// Motorista aceita. Transação: se dois aceitarem ao mesmo tempo, só o
// primeiro leva; o outro recebe "já foi aceita por outro motorista".
exports.aceitarCorrida = onCall(async (request) => {
    const uid = exigirLogin(request);
    const corridaId = String((request.data && request.data.corridaId) || '');
    if (!corridaId) throw new HttpsError('invalid-argument', 'Corrida inválida.');
    const db = admin.firestore();
    const motoristaRef = db.collection('motoristas').doc(uid);
    const corridaRef = db.collection('corridas').doc(corridaId);

    await db.runTransaction(async (tx) => {
        const motoristaDoc = await tx.get(motoristaRef);
        const corridaDoc = await tx.get(corridaRef);
        if (!motoristaDoc.exists) throw new HttpsError('failed-precondition', 'Cadastro de motorista não encontrado.');
        const m = motoristaDoc.data();
        if (m.status !== 'aprovado') throw new HttpsError('permission-denied', 'Seu cadastro ainda não está aprovado.');
        if (!m.online) throw new HttpsError('failed-precondition', 'Fique online para aceitar corridas.');
        if (m.corridaAtualId) throw new HttpsError('failed-precondition', 'Você já está em uma corrida.');
        if (!corridaDoc.exists || corridaDoc.data().status !== STATUS.PROCURANDO) {
            throw new HttpsError('failed-precondition', 'Esta corrida já foi aceita por outro motorista ou cancelada.');
        }
        if (corridaDoc.data().passageiroId === uid) {
            throw new HttpsError('failed-precondition', 'Você não pode aceitar a sua própria corrida.');
        }
        tx.update(corridaRef, {
            status: STATUS.ACEITA,
            motoristaId: uid,
            motoristaNome: m.nomeCompleto || '',
            motoristaFotoUrl: m.fotoUrl || null,
            motoristaTelefone: m.telefone || null,
            veiculo: m.veiculo || null,
            motoristaLocal: m.localizacao || null,
            aceitaEm: admin.firestore.FieldValue.serverTimestamp(),
        });
        tx.update(motoristaRef, { corridaAtualId: corridaId });
    });
    return { ok: true };
});

// Ações depois do pedido:
//   embarcou  (motorista)            aceita → em_andamento
//   finalizar (motorista)            em_andamento → finalizada
//   cancelar  (passageiro/motorista) procurando/aceita → cancelada
exports.acaoCorrida = onCall(async (request) => {
    const uid = exigirLogin(request);
    const corridaId = String((request.data && request.data.corridaId) || '');
    const acao = String((request.data && request.data.acao) || '');
    if (!corridaId) throw new HttpsError('invalid-argument', 'Corrida inválida.');
    const db = admin.firestore();
    const corridaRef = db.collection('corridas').doc(corridaId);
    const agora = admin.firestore.FieldValue.serverTimestamp();

    await db.runTransaction(async (tx) => {
        const doc = await tx.get(corridaRef);
        if (!doc.exists) throw new HttpsError('not-found', 'Corrida não encontrada.');
        const c = doc.data();
        const ehPassageiro = c.passageiroId === uid;
        const ehMotorista = c.motoristaId === uid;
        if (!ehPassageiro && !ehMotorista) throw new HttpsError('permission-denied', 'Esta corrida não é sua.');

        const liberarPerfis = () => {
            tx.update(db.collection('passageiros').doc(c.passageiroId), { corridaAtualId: null });
            if (c.motoristaId) tx.update(db.collection('motoristas').doc(c.motoristaId), { corridaAtualId: null });
        };

        if (acao === 'embarcou') {
            if (!ehMotorista || c.status !== STATUS.ACEITA) throw new HttpsError('failed-precondition', 'Ação não permitida agora.');
            tx.update(corridaRef, { status: STATUS.EM_ANDAMENTO, embarqueEm: agora });
        } else if (acao === 'finalizar') {
            if (!ehMotorista || c.status !== STATUS.EM_ANDAMENTO) throw new HttpsError('failed-precondition', 'Ação não permitida agora.');
            tx.update(corridaRef, { status: STATUS.FINALIZADA, finalizadaEm: agora });
            tx.update(db.collection('motoristas').doc(uid), { totalCorridas: admin.firestore.FieldValue.increment(1), corridaAtualId: null });
            tx.update(db.collection('passageiros').doc(c.passageiroId), { totalCorridas: admin.firestore.FieldValue.increment(1), corridaAtualId: null });
        } else if (acao === 'cancelar') {
            if (![STATUS.PROCURANDO, STATUS.ACEITA].includes(c.status)) {
                throw new HttpsError('failed-precondition', 'A corrida não pode mais ser cancelada.');
            }
            tx.update(corridaRef, { status: STATUS.CANCELADA, canceladaPor: ehMotorista ? 'motorista' : 'passageiro', canceladaEm: agora });
            liberarPerfis();
        } else {
            throw new HttpsError('invalid-argument', 'Ação desconhecida.');
        }
    });
    return { ok: true };
});

