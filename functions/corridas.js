// Ciclo da corrida: estimar, pedir, aceitar, embarcar, parada extra,
// finalizar, cancelar. Preço, aceite e mudança de situação acontecem SÓ aqui
// (Admin SDK): firestore.rules não deixa o app criar corrida nem mudar
// status/valor. As regras de preço ficam em tarifa.js.
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');
const { TARIFA_PADRAO, calcularCorrida } = require('./tarifa');
const notificar = require('./notificacoes');

const MAPS_SERVER_KEY = process.env.MAPS_SERVER_KEY || '';

// Precisa bater com StatusCorrida no app.
const STATUS = {
    PROCURANDO: 'procurando',
    ACEITA: 'aceita',
    EM_ANDAMENTO: 'em_andamento',
    FINALIZADA: 'finalizada',
    CANCELADA: 'cancelada',
};
const ATIVAS = [STATUS.PROCURANDO, STATUS.ACEITA, STATUS.EM_ANDAMENTO];
const MAX_PARADAS = 3;

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

// Antes de existir motorista: só a viagem (deslocamento 0) — é o "a partir
// de" que o passageiro vê. O valor final sai no aceite.
async function estimar(origem, destino, quartoPassageiro) {
    const [rota, tarifa] = await Promise.all([calcularRota(origem, destino), lerTarifa()]);
    if (rota.distanciaMetros < 200) {
        throw new HttpsError('invalid-argument', 'A partida e o destino estão perto demais.');
    }
    const calculo = calcularCorrida(tarifa, {
        viagemMetros: rota.distanciaMetros,
        viagemSegundos: rota.duracaoSegundos,
        quartoPassageiro,
    });
    return { rota, tarifa, calculo };
}

exports.estimarCorrida = onCall(async (request) => {
    exigirLogin(request);
    const dados = request.data || {};
    const { rota, calculo } = await estimar(
        lerLocal(dados.origem, 'partida'), lerLocal(dados.destino, 'destino'), dados.quartoPassageiro === true
    );
    return { ...rota, valor: calculo.valorTotal, calculo };
});

// Cria a corrida (procurando motorista) e avisa os motoristas próximos.
exports.pedirCorrida = onCall(async (request) => {
    const uid = exigirLogin(request);
    const dados = request.data || {};
    const origem = lerLocal(dados.origem, 'partida');
    const destino = lerLocal(dados.destino, 'destino');
    const quartoPassageiro = dados.quartoPassageiro === true;
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

    const { rota, tarifa, calculo } = await estimar(origem, destino, quartoPassageiro);
    const corridaRef = db.collection('corridas').doc();
    const corrida = {
        status: STATUS.PROCURANDO,
        passageiroId: uid,
        passageiroNome: p.nomeCompleto || '',
        passageiroFotoUrl: p.fotoUrl || null,
        passageiroTelefone: p.telefone || null,
        origem,
        destino,
        paradas: [],
        quartoPassageiro,
        distanciaMetros: rota.distanciaMetros,
        duracaoSegundos: rota.duracaoSegundos,
        polyline: rota.polyline,
        // Prévia sem o deslocamento; aceitarCorrida grava o valor final.
        valorEstimado: calculo.valorTotal,
        valor: calculo.valorTotal,
        calculo,
        formaPagamento: 'direto',
        criadaEm: admin.firestore.FieldValue.serverTimestamp(),
    };
    const lote = db.batch();
    lote.set(corridaRef, corrida);
    lote.update(perfilRef, { corridaAtualId: corridaRef.id });
    await lote.commit();

    await notificar.avisarMotoristasProximos(corridaRef.id, corrida, tarifa.raioBuscaKm);
    return { corridaId: corridaRef.id, valor: calculo.valorTotal };
});

// Motorista aceita. O preço final usa a rota real do motorista até o
// passageiro. Transação: se dois aceitarem juntos, só o primeiro leva.
exports.aceitarCorrida = onCall(async (request) => {
    const uid = exigirLogin(request);
    const corridaId = String((request.data && request.data.corridaId) || '');
    if (!corridaId) throw new HttpsError('invalid-argument', 'Corrida inválida.');
    const db = admin.firestore();
    const motoristaRef = db.collection('motoristas').doc(uid);
    const corridaRef = db.collection('corridas').doc(corridaId);

    // Rota e preço FORA da transação (chamada externa); a transação só
    // confere que nada mudou e grava.
    const [motoristaAntes, corridaAntes, tarifa] = await Promise.all([motoristaRef.get(), corridaRef.get(), lerTarifa()]);
    if (!motoristaAntes.exists) throw new HttpsError('failed-precondition', 'Cadastro de motorista não encontrado.');
    if (!corridaAntes.exists || corridaAntes.data().status !== STATUS.PROCURANDO) {
        throw new HttpsError('failed-precondition', 'Esta corrida já foi aceita por outro motorista ou cancelada.');
    }
    const local = motoristaAntes.data().localizacao;
    if (!local) throw new HttpsError('failed-precondition', 'Ative a localização para aceitar corridas.');
    const c0 = corridaAntes.data();
    const deslocamento = await calcularRota(local, c0.origem);
    const calculo = calcularCorrida(tarifa, {
        deslocamentoMetros: deslocamento.distanciaMetros,
        deslocamentoSegundos: deslocamento.duracaoSegundos,
        viagemMetros: c0.distanciaMetros,
        viagemSegundos: c0.duracaoSegundos,
        quartoPassageiro: c0.quartoPassageiro === true,
        paradas: 0,
    });

    await db.runTransaction(async (tx) => {
        const motoristaDoc = await tx.get(motoristaRef);
        const corridaDoc = await tx.get(corridaRef);
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
            // Caminho do motorista até o passageiro, desenhado no mapa do app
            // (a navegação acontece dentro do Start, não no Google Maps).
            polylineDeslocamento: deslocamento.polyline,
            valor: calculo.valorTotal,
            calculo,
            aceitaEm: admin.firestore.FieldValue.serverTimestamp(),
        });
        tx.update(motoristaRef, { corridaAtualId: corridaId });
    });

    const v = m2(calculo.valorTotal);
    await notificar.avisarPassageiro(c0.passageiroId, 'Motorista a caminho',
        (motoristaAntes.data().nomeCompleto || 'O motorista') + ' aceitou sua corrida. Valor: ' + v + '.', corridaId);
    return { ok: true, valor: calculo.valorTotal };
});

function m2(v) {
    return 'R$ ' + v.toFixed(2).replace('.', ',');
}

// Passageiro pede uma parada extra durante a corrida: + valorPorParada.
exports.adicionarParada = onCall(async (request) => {
    const uid = exigirLogin(request);
    const corridaId = String((request.data && request.data.corridaId) || '');
    const parada = lerLocal(request.data && request.data.parada, 'parada');
    const db = admin.firestore();
    const corridaRef = db.collection('corridas').doc(corridaId);
    const tarifa = await lerTarifa();
    let motoristaId = null;
    let valorNovo = 0;

    await db.runTransaction(async (tx) => {
        const doc = await tx.get(corridaRef);
        if (!doc.exists) throw new HttpsError('not-found', 'Corrida não encontrada.');
        const c = doc.data();
        if (c.passageiroId !== uid) throw new HttpsError('permission-denied', 'Esta corrida não é sua.');
        if (![STATUS.ACEITA, STATUS.EM_ANDAMENTO].includes(c.status)) {
            throw new HttpsError('failed-precondition', 'Só dá pra adicionar parada com a corrida em andamento.');
        }
        const paradas = [...(c.paradas || []), parada];
        if (paradas.length > MAX_PARADAS) throw new HttpsError('failed-precondition', 'Máximo de ' + MAX_PARADAS + ' paradas por corrida.');
        const base = c.calculo || {};
        const calculo = calcularCorrida(tarifa, {
            deslocamentoMetros: base.deslocamentoMetros || 0,
            deslocamentoSegundos: base.deslocamentoSegundos || 0,
            viagemMetros: c.distanciaMetros,
            viagemSegundos: c.duracaoSegundos,
            quartoPassageiro: c.quartoPassageiro === true,
            paradas: paradas.length,
        });
        motoristaId = c.motoristaId;
        valorNovo = calculo.valorTotal;
        tx.update(corridaRef, { paradas, calculo, valor: calculo.valorTotal });
    });

    await notificar.avisarMotorista(motoristaId, 'Nova parada na corrida',
        (parada.endereco || 'Parada extra') + ' · novo valor ' + m2(valorNovo), corridaId);
    return { ok: true, valor: valorNovo };
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
    let c = null;
    let porMotorista = false;

    await db.runTransaction(async (tx) => {
        const doc = await tx.get(corridaRef);
        if (!doc.exists) throw new HttpsError('not-found', 'Corrida não encontrada.');
        c = doc.data();
        const ehPassageiro = c.passageiroId === uid;
        const ehMotorista = c.motoristaId === uid;
        porMotorista = ehMotorista;
        if (!ehPassageiro && !ehMotorista) throw new HttpsError('permission-denied', 'Esta corrida não é sua.');

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
            tx.update(db.collection('passageiros').doc(c.passageiroId), { corridaAtualId: null });
            if (c.motoristaId) tx.update(db.collection('motoristas').doc(c.motoristaId), { corridaAtualId: null });
        } else {
            throw new HttpsError('invalid-argument', 'Ação desconhecida.');
        }
    });

    if (acao === 'finalizar') {
        await notificar.avisarPassageiro(c.passageiroId, 'Você chegou!',
            'Corrida finalizada. Pague ' + m2(c.valor) + ' ao motorista (dinheiro ou Pix).', corridaId);
    } else if (acao === 'cancelar') {
        if (porMotorista) {
            await notificar.avisarPassageiro(c.passageiroId, 'Corrida cancelada', 'O motorista cancelou. Você pode pedir outra corrida.', corridaId);
        } else if (c.motoristaId) {
            await notificar.avisarMotorista(c.motoristaId, 'Corrida cancelada', 'O passageiro cancelou a corrida.', corridaId);
        }
    }
    return { ok: true };
});
