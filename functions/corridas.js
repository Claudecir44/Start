// Ciclo da corrida: estimar, pedir, aceitar, embarcar, parada extra,
// finalizar, cancelar. Preço, aceite e mudança de situação acontecem SÓ aqui
// (Admin SDK): firestore.rules não deixa o app criar corrida nem mudar
// status/valor. As regras de preço ficam em tarifa.js e a busca de motorista
// por etapas (2/4/10 km) em busca.js.
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');
const { calcularCorrida } = require('./tarifa');
const { STATUS, ATIVAS, exigirLogin, lerLocal, lerTarifa, calcularRota, m2 } = require('./comum');
const busca = require('./busca');
const notificar = require('./notificacoes');

const MAX_PARADAS = 3;

// O chat continua aberto por 1 hora depois do fim da corrida (passageiro
// esqueceu algo no carro); depois fica só pra leitura (chat.js).
const CHAT_APOS_FIM_MS = 60 * 60 * 1000;
const fimDoChat = () => admin.firestore.Timestamp.fromMillis(Date.now() + CHAT_APOS_FIM_MS);

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
        // Busca antiga com prazo vencido (app fechado) é encerrada aqui.
        await busca.processar(db.collection('corridas').doc(p.corridaAtualId));
        const atual = await db.collection('corridas').doc(p.corridaAtualId).get();
        if (atual.exists && ATIVAS.includes(atual.data().status)) {
            throw new HttpsError('failed-precondition', 'Você já tem uma corrida em andamento.');
        }
    }

    const { rota, calculo } = await estimar(origem, destino, quartoPassageiro);
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

    // Etapa 1 (até 2 km) e o push pros motoristas dela.
    await busca.iniciar(corridaRef);
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

    // Prazo da busca vencido (pergunta sem resposta) encerra antes do aceite.
    await busca.processar(corridaRef);

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
    busca.conferirAceite(c0, local);
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
        if (corridaDoc.data().busca && corridaDoc.data().busca.pergunta) {
            throw new HttpsError('failed-precondition', 'O passageiro está decidindo se continua. Tente de novo em instantes.');
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
            'busca.proximaEtapaEm': null,
            'busca.limiteEm': null,
        });
        tx.update(motoristaRef, { corridaAtualId: corridaId, ultimoChatId: corridaId });
        // Chat entre os dois abre no aceite (chat.js; fecha 1 h depois do fim).
        const c = corridaDoc.data();
        tx.set(db.collection('chats').doc(corridaId), {
            corridaId,
            passageiroId: c.passageiroId,
            passageiroNome: c.passageiroNome || '',
            passageiroFotoUrl: c.passageiroFotoUrl || null,
            motoristaId: uid,
            motoristaNome: m.nomeCompleto || '',
            motoristaFotoUrl: m.fotoUrl || null,
            abertoEm: admin.firestore.FieldValue.serverTimestamp(),
            fechaEm: null,
            naoLidasPassageiro: 0,
            naoLidasMotorista: 0,
            ultimaMensagemEm: null,
        });
        tx.update(db.collection('passageiros').doc(c.passageiroId), { ultimoChatId: corridaId });
    });

    const v = m2(calculo.valorTotal);
    await notificar.avisarPassageiro(c0.passageiroId, 'Motorista a caminho',
        (motoristaAntes.data().nomeCompleto || 'O motorista') + ' aceitou sua corrida. Valor: ' + v + '.', corridaId);
    return { ok: true, valor: calculo.valorTotal };
});

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
            tx.set(db.collection('chats').doc(corridaId), { fechaEm: fimDoChat() }, { merge: true });
        } else if (acao === 'cancelar') {
            if (![STATUS.PROCURANDO, STATUS.ACEITA].includes(c.status)) {
                throw new HttpsError('failed-precondition', 'A corrida não pode mais ser cancelada.');
            }
            tx.update(corridaRef, { status: STATUS.CANCELADA, canceladaPor: ehMotorista ? 'motorista' : 'passageiro', canceladaEm: agora });
            tx.update(db.collection('passageiros').doc(c.passageiroId), { corridaAtualId: null });
            if (c.motoristaId) {
                tx.update(db.collection('motoristas').doc(c.motoristaId), { corridaAtualId: null });
                tx.set(db.collection('chats').doc(corridaId), { fechaEm: fimDoChat() }, { merge: true });
            }
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
