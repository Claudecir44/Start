// Notificações push (FCM). O app grava o token do aparelho no próprio perfil
// (passageiros/{uid}.fcmToken, motoristas/{uid}.fcmToken). Falha de envio
// nunca derruba a ação principal (pedir, aceitar...): só fica no log.
const admin = require('firebase-admin');

// Precisam bater com os canais criados em StartApplication.kt.
const CANAL_CORRIDAS = 'corridas';
const CANAL_AVISOS = 'avisos';

function distanciaMetros(a, b) {
    const r = 6371000;
    const rad = (g) => (g * Math.PI) / 180;
    const dLat = rad(b.lat - a.lat);
    const dLng = rad(b.lng - a.lng);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
    return 2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

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

// "Nova corrida" pra motoristas aprovados, online, livres e perto da partida.
async function avisarMotoristasProximos(corridaId, corrida, raioKm) {
    const snap = await admin.firestore().collection('motoristas')
        .where('online', '==', true)
        .where('status', '==', 'aprovado')
        .get();
    const tokens = snap.docs
        .map((d) => d.data())
        .filter((m) => !m.corridaAtualId && m.fcmToken && m.localizacao)
        .filter((m) => distanciaMetros(m.localizacao, corrida.origem) <= raioKm * 1000)
        .map((m) => m.fcmToken);
    const extra = corrida.quartoPassageiro ? ' · 4 passageiros (+20%)' : '';
    await enviar(tokens, {
        titulo: 'Nova corrida perto de você',
        texto: (corrida.origem.endereco || 'Partida próxima') + extra,
        canal: CANAL_CORRIDAS,
        dados: { tipo: 'nova_corrida', corridaId },
    });
}

async function avisarPassageiro(passageiroId, titulo, texto, corridaId) {
    const token = await tokenDe('passageiros', passageiroId);
    await enviar([token], { titulo, texto, canal: CANAL_AVISOS, dados: { tipo: 'corrida', corridaId } });
}

async function avisarMotorista(motoristaId, titulo, texto, corridaId) {
    const token = await tokenDe('motoristas', motoristaId);
    await enviar([token], { titulo, texto, canal: CANAL_AVISOS, dados: { tipo: 'corrida', corridaId } });
}

module.exports = { avisarMotoristasProximos, avisarPassageiro, avisarMotorista };
