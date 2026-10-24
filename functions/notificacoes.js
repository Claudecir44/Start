// Notificações push (FCM). O app grava o token do aparelho no próprio perfil
// (passageiros/{uid}.fcmToken, motoristas/{uid}.fcmToken). Falha de envio
// nunca derruba a ação principal (pedir, aceitar...): só fica no log.
const admin = require('firebase-admin');

// Precisam bater com os canais criados em StartApplication.kt.
const CANAL_CORRIDAS = 'corridas';
const CANAL_AVISOS = 'avisos';

async function enviar(tokens, { titulo, texto, canal, dados }) {
    const lista = [...new Set(tokens.filter(Boolean))];
    if (lista.length === 0) return;
    try {
        const resposta = await admin.messaging().sendEachForMulticast({
            tokens: lista,
            notification: { title: titulo, body: texto },
            data: Object.fromEntries(Object.entries(dados || {}).map(([k, v]) => [k, String(v)])),
            android: {
                priority: 'high',
                notification: { channelId: canal, sound: 'default' },
            },
        });
        if (resposta.failureCount > 0) {
            console.warn('Push: ' + resposta.failureCount + ' de ' + lista.length + ' falharam.');
        }
    } catch (e) {
        console.warn('Push falhou:', e.message);
    }
}

async function tokenDe(colecao, uid) {
    if (!uid) return null;
    const doc = await admin.firestore().collection(colecao).doc(uid).get();
    return doc.exists ? doc.get('fcmToken') : null;
}

// "Nova corrida" pros motoristas da etapa da busca (lista de busca.js:
// aprovados, online, livres e dentro do raio).
async function avisarMotoristas(corridaId, corrida, motoristas) {
    const tokens = motoristas.map((m) => m.fcmToken);
    const extra = corrida.quartoPassageiro ? ' · 4 passageiros (+20%)' : '';
    await enviar(tokens, {
        titulo: 'Nova corrida perto de você',
        texto: (corrida.origem.endereco || 'Partida próxima') + extra,
        canal: CANAL_CORRIDAS,
        dados: { tipo: 'nova_corrida', corridaId },
    });
}

// tipo: 'corrida' ou 'chat' (o app abre a tela certa ao tocar).
async function avisarPassageiro(passageiroId, titulo, texto, corridaId, tipo = 'corrida') {
    const token = await tokenDe('passageiros', passageiroId);
    await enviar([token], { titulo, texto, canal: CANAL_AVISOS, dados: { tipo, corridaId } });
}

async function avisarMotorista(motoristaId, titulo, texto, corridaId, tipo = 'corrida') {
    const token = await tokenDe('motoristas', motoristaId);
    await enviar([token], { titulo, texto, canal: CANAL_AVISOS, dados: { tipo, corridaId } });
}

module.exports = { avisarMotoristas, avisarPassageiro, avisarMotorista };
