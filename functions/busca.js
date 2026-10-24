// Busca de motorista POR ETAPAS (regras do dono, 24/10/2026):
//  1. Motoristas até 2 km da partida. Ninguém nessa distância (ou ninguém
//     aceitou em ESPERA_ETAPA_MS) → etapa 2.
//  2. Até 4 km. Sem aceite → pergunta ao passageiro: "Não há motorista
//     disponível na sua região próxima. Deseja continuar?"
//     Não → encerra. Sim → calcula o novo valor com o motorista livre mais
//     perto (até raioBuscaKm, 10 km: deslocamento dele + a viagem) e pergunta
//     se aceita. Não → encerra. Sim → etapa 3.
//  3. Até 10 km.
//  Em qualquer etapa, 3 minutos de busca (TEMPO_BUSCA_MS, sem contar o tempo
//  das perguntas) sem aceite → "Motorista não disponível. Encerrar ou
//  continuar?" (continuar = mais 3 minutos até 10 km).
//  Pergunta sem resposta em PRAZO_RESPOSTA_MS → a busca é encerrada sozinha.
//
// Estado em corridas/{id}.busca. Não há agendador no servidor: quem faz o
// relógio andar é o app do passageiro (chama avancarBusca nos prazos) e,
// como garantia, pedirCorrida/aceitarCorrida, que processam a busca antes de
// agir. Assim uma busca abandonada (app fechado) nunca é aceita depois do
// prazo, e o motorista já ignora no próprio app as que passaram do prazo.
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');
const { calcularCorrida } = require('./tarifa');
const { STATUS, exigirLogin, lerTarifa, calcularRota, distanciaMetros, m2 } = require('./comum');
const notificar = require('./notificacoes');

// Precisam bater com BuscaCorrida no app.
const RAIO_ETAPA_1_KM = 2;
const RAIO_ETAPA_2_KM = 4;
const ESPERA_ETAPA_MS = 40 * 1000;
const TEMPO_BUSCA_MS = 3 * 60 * 1000;
const PRAZO_RESPOSTA_MS = 60 * 1000;
const PERGUNTA = {
    REGIAO: 'regiao',               // "não há motorista na sua região próxima"
    NOVO_VALOR: 'novo_valor',       // valor recalculado até 10 km
    SEM_MOTORISTA: 'sem_motorista', // 3 minutos sem aceite
};

const ts = (ms) => admin.firestore.Timestamp.fromMillis(ms);
const ms = (t) => (t && typeof t.toMillis === 'function' ? t.toMillis() : null);

// Motoristas que podem receber a corrida: aprovados, online, livres, com
// posição e até raioKm da partida (linha reta), do mais perto ao mais longe.
async function motoristasDisponiveis(corrida, raioKm) {
    const snap = await admin.firestore().collection('motoristas')
        .where('online', '==', true)
        .where('status', '==', 'aprovado')
        .get();
    return snap.docs
        .map((d) => ({ uid: d.id, ...d.data() }))
        .filter((m) => !m.corridaAtualId && m.localizacao && m.uid !== corrida.passageiroId)
        .map((m) => ({ ...m, metros: distanciaMetros(m.localizacao, corrida.origem) }))
        .filter((m) => m.metros <= raioKm * 1000)
        .sort((a, b) => a.metros - b.metros);
}

// Grava só se a corrida ainda está procurando e a busca não mudou desde a
// leitura (dois processamentos ao mesmo tempo não pulam etapas duas vezes).
async function gravarSeIgual(ref, buscaLida, mudancas) {
    return admin.firestore().runTransaction(async (tx) => {
        const doc = await tx.get(ref);
        const b = doc.exists ? doc.data().busca : null;
        if (!b || doc.data().status !== STATUS.PROCURANDO
            || b.etapa !== buscaLida.etapa || b.pergunta !== buscaLida.pergunta) return false;
        tx.update(ref, mudancas);
        return true;
    });
}

// Entra na etapa n (raio maior). Etapas 1 e 2 sem ninguém no raio pulam na
// hora; a 3 espera até o fim dos 3 minutos mesmo vazia (alguém pode ficar
// online). Avisa por push só quem ainda não tinha sido avisado.
async function entrarEtapa(ref, c, n, agora) {
    const tarifa = await lerTarifa();
    const raioKm = n === 1 ? RAIO_ETAPA_1_KM : n === 2 ? RAIO_ETAPA_2_KM : Number(tarifa.raioBuscaKm) || 10;
    const disponiveis = await motoristasDisponiveis(c, raioKm);
    if (n < 3 && disponiveis.length === 0) {
        if (n === 1) return entrarEtapa(ref, c, 2, agora);
        return perguntar(ref, c, PERGUNTA.REGIAO, agora);
    }
    const avisados = c.busca.avisados || [];
    const novos = disponiveis.filter((m) => !avisados.includes(m.uid));
    const busca = {
        ...c.busca,
        etapa: n,
        raioKm,
        proximaEtapaEm: n < 3 ? ts(agora + ESPERA_ETAPA_MS) : null,
        avisados: [...avisados, ...novos.map((m) => m.uid)],
    };
    if (!await gravarSeIgual(ref, c.busca, { busca })) return;
    await notificar.avisarMotoristas(ref.id, c, novos.filter((m) => m.fcmToken));
}

// Pausa o relógio dos 3 minutos e pergunta ao passageiro.
async function perguntar(ref, c, pergunta, agora, extras = {}) {
    const b = c.busca;
    const limite = ms(b.limiteEm);
    const busca = {
        ...b,
        pergunta,
        respostaAte: ts(agora + PRAZO_RESPOSTA_MS),
        // Já pausada (uma pergunta depois da outra): mantém o que restava.
        restanteMs: limite != null ? Math.max(0, limite - agora) : (b.restanteMs || 0),
        limiteEm: null,
        proximaEtapaEm: null,
        valorNovo: null,
        calculoNovo: null,
        ...extras,
    };
    if (!await gravarSeIgual(ref, b, { busca })) return;
    const textos = {
        [PERGUNTA.REGIAO]: ['Sem motorista por perto', 'Não há motorista disponível na sua região próxima. Deseja continuar?'],
        [PERGUNTA.NOVO_VALOR]: ['Novo valor da corrida', 'Buscando mais longe, a corrida fica em ' + m2(extras.valorNovo || 0) + '. Aceita?'],
        [PERGUNTA.SEM_MOTORISTA]: ['Motorista não disponível', 'Nenhum motorista aceitou. Encerrar ou continuar procurando?'],
    };
    const [titulo, texto] = textos[pergunta];
    await notificar.avisarPassageiro(c.passageiroId, titulo, texto, ref.id);
}

// Encerra a busca (corrida cancelada) e solta o passageiro.
async function encerrar(ref, c, canceladaPor, motivo) {
    const db = admin.firestore();
    const ok = await db.runTransaction(async (tx) => {
        const doc = await tx.get(ref);
        if (!doc.exists || doc.data().status !== STATUS.PROCURANDO) return false;
        tx.update(ref, {
            status: STATUS.CANCELADA,
            canceladaPor,
            motivoCancelamento: motivo,
            canceladaEm: admin.firestore.FieldValue.serverTimestamp(),
            'busca.pergunta': null,
        });
        tx.update(db.collection('passageiros').doc(c.passageiroId), { corridaAtualId: null });
        return true;
    });
    if (ok && canceladaPor === 'sistema') {
        await notificar.avisarPassageiro(c.passageiroId, 'Busca encerrada',
            'Você não respondeu a tempo, então encerramos a busca. Peça de novo quando quiser.', ref.id);
    }
}

// Primeira etapa (chamada por pedirCorrida logo depois de criar a corrida).
async function iniciar(ref) {
    const doc = await ref.get();
    const agora = Date.now();
    const c = doc.data();
    const busca = {
        etapa: 0,
        raioKm: 0,
        limiteEm: ts(agora + TEMPO_BUSCA_MS),
        restanteMs: null,
        proximaEtapaEm: null,
        pergunta: null,
        respostaAte: null,
        valorNovo: null,
        calculoNovo: null,
        avisados: [],
    };
    await ref.update({ busca });
    await entrarEtapa(ref, { ...c, busca }, 1, agora);
}

// Faz o relógio andar: prazo de resposta, 3 minutos e troca de etapa.
// Pode ser chamada quantas vezes for (só age quando um prazo venceu).
async function processar(ref) {
    const doc = await ref.get();
    if (!doc.exists) return;
    const c = doc.data();
    const b = c.busca;
    if (c.status !== STATUS.PROCURANDO || !b) return;
    const agora = Date.now();
    if (b.pergunta) {
        if (agora >= ms(b.respostaAte)) await encerrar(ref, c, 'sistema', 'sem_resposta');
        return;
    }
    if (ms(b.limiteEm) != null && agora >= ms(b.limiteEm)) {
        await perguntar(ref, c, PERGUNTA.SEM_MOTORISTA, agora);
        return;
    }
    if (b.etapa < 3 && ms(b.proximaEtapaEm) != null && agora >= ms(b.proximaEtapaEm)) {
        if (b.etapa <= 1) await entrarEtapa(ref, c, 2, agora);
        else await perguntar(ref, c, PERGUNTA.REGIAO, agora);
    }
}

// Retoma depois de um "sim": o relógio volta a contar e entra na etapa 3.
async function retomar(ref, c, agora, restanteMs, mudancas = {}) {
    const busca = {
        ...c.busca,
        pergunta: null,
        respostaAte: null,
        valorNovo: null,
        calculoNovo: null,
        restanteMs: null,
        // Nunca menos que uma etapa, pra dar tempo de alguém aceitar.
        limiteEm: ts(agora + Math.max(restanteMs, ESPERA_ETAPA_MS)),
    };
    if (!await gravarSeIgual(ref, c.busca, { busca, ...mudancas })) return;
    await entrarEtapa(ref, { ...c, ...mudancas, busca }, 3, agora);
}

function corridaDoPassageiro(doc, uid) {
    if (!doc.exists) throw new HttpsError('not-found', 'Corrida não encontrada.');
    const c = doc.data();
    if (c.passageiroId !== uid) throw new HttpsError('permission-denied', 'Esta corrida não é sua.');
    return c;
}

// App do passageiro chama quando um prazo da busca vence.
exports.avancarBusca = onCall(async (request) => {
    const uid = exigirLogin(request);
    const corridaId = String((request.data && request.data.corridaId) || '');
    if (!corridaId) throw new HttpsError('invalid-argument', 'Corrida inválida.');
    const ref = admin.firestore().collection('corridas').doc(corridaId);
    corridaDoPassageiro(await ref.get(), uid);
    await processar(ref);
    return { ok: true };
});

// Resposta do passageiro à pergunta da busca: sim (continuar) ou não (encerrar).
exports.responderBusca = onCall(async (request) => {
    const uid = exigirLogin(request);
    const corridaId = String((request.data && request.data.corridaId) || '');
    const sim = request.data && request.data.continuar === true;
    if (!corridaId) throw new HttpsError('invalid-argument', 'Corrida inválida.');
    const ref = admin.firestore().collection('corridas').doc(corridaId);
    const c = corridaDoPassageiro(await ref.get(), uid);
    const b = c.busca;
    const agora = Date.now();
    if (c.status !== STATUS.PROCURANDO || !b || !b.pergunta) {
        throw new HttpsError('failed-precondition', 'Esta busca não está esperando resposta.');
    }
    if (agora >= ms(b.respostaAte)) {
        await encerrar(ref, c, 'sistema', 'sem_resposta');
        throw new HttpsError('deadline-exceeded', 'O tempo para responder acabou e a busca foi encerrada.');
    }
    if (!sim) {
        await encerrar(ref, c, 'passageiro', 'nao_quis_continuar');
        return { ok: true };
    }

    if (b.pergunta === PERGUNTA.REGIAO) {
        // Novo valor com o motorista livre mais perto até o raio máximo:
        // a rota dele até a partida + a viagem.
        const tarifa = await lerTarifa();
        const [maisPerto] = await motoristasDisponiveis(c, Number(tarifa.raioBuscaKm) || 10);
        if (!maisPerto) {
            await perguntar(ref, c, PERGUNTA.SEM_MOTORISTA, agora);
            return { ok: true };
        }
        const deslocamento = await calcularRota(maisPerto.localizacao, c.origem);
        const calculoNovo = calcularCorrida(tarifa, {
            deslocamentoMetros: deslocamento.distanciaMetros,
            deslocamentoSegundos: deslocamento.duracaoSegundos,
            viagemMetros: c.distanciaMetros,
            viagemSegundos: c.duracaoSegundos,
            quartoPassageiro: c.quartoPassageiro === true,
            paradas: 0,
        });
        await perguntar(ref, c, PERGUNTA.NOVO_VALOR, agora, { valorNovo: calculoNovo.valorTotal, calculoNovo });
    } else if (b.pergunta === PERGUNTA.NOVO_VALOR) {
        await retomar(ref, c, agora, b.restanteMs || 0, { valor: b.valorNovo, calculo: b.calculoNovo });
    } else {
        // Sem motorista em 3 minutos: mais 3 minutos até 10 km.
        await retomar(ref, c, agora, TEMPO_BUSCA_MS);
    }
    return { ok: true };
});

// O motorista só pode aceitar se a busca não está parada numa pergunta e se
// ele está dentro do raio da etapa (com folga pro carro andando).
function conferirAceite(c, localMotorista) {
    const b = c.busca;
    if (!b) return;
    if (b.pergunta) throw new HttpsError('failed-precondition', 'O passageiro está decidindo se continua. Tente de novo em instantes.');
    if (localMotorista && b.raioKm > 0 && distanciaMetros(localMotorista, c.origem) > b.raioKm * 1000 + FOLGA_RAIO_M) {
        throw new HttpsError('failed-precondition', 'Esta corrida está sendo oferecida a motoristas mais perto.');
    }
}
const FOLGA_RAIO_M = 500;

module.exports = {
    avancarBusca: exports.avancarBusca,
    responderBusca: exports.responderBusca,
    iniciar,
    processar,
    conferirAceite,
};
