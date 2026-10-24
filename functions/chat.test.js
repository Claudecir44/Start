// Teste do envio do chat (enviarMensagemChat) no emulador do Firestore.
// Rodar da raiz do projeto:
//   npx --yes firebase-tools emulators:exec --only firestore "node functions/chat.test.js"
process.env.GCLOUD_PROJECT = process.env.GCLOUD_PROJECT || 'start-3835f';
const assert = require('assert');
const admin = require('firebase-admin');
if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Rode dentro do emulador (FIRESTORE_EMULATOR_HOST).');
admin.initializeApp({ projectId: process.env.GCLOUD_PROJECT });
const { enviarMensagemChat } = require('./chat');
const db = admin.firestore();
const enviar = (uid, chatId, conteudo = 'ENC1:abc') => enviarMensagemChat.run({ auth: { uid }, data: { chatId, conteudo } });

async function main() {
    await db.doc('chats/c1').set({ passageiroId: 'p', motoristaId: 'm', passageiroNome: 'Ana', motoristaNome: 'João',
        fechaEm: null, naoLidasPassageiro: 0, naoLidasMotorista: 0 });
    await db.doc('chats/fechado').set({ passageiroId: 'p', motoristaId: 'm', naoLidasPassageiro: 0, naoLidasMotorista: 0,
        fechaEm: admin.firestore.Timestamp.fromMillis(Date.now() - 1000) });

    await enviar('p', 'c1');
    await enviar('p', 'c1');
    await enviar('m', 'c1');
    const c = (await db.doc('chats/c1').get()).data();
    assert.strictEqual(c.naoLidasMotorista, 2);
    assert.strictEqual(c.naoLidasPassageiro, 1);
    const msgs = (await db.collection('chats/c1/mensagens').get()).docs.map((d) => d.data());
    assert.strictEqual(msgs.length, 3);
    assert.ok(msgs.every((m) => m.lida === false && m.conteudo === 'ENC1:abc'));
    assert.strictEqual(msgs.filter((m) => m.remetenteId === 'p' && m.destinatarioId === 'm').length, 2);
    console.log('ok: envio grava a mensagem e soma as não lidas do outro lado');

    await assert.rejects(enviar('x', 'c1'), /não é seu/);
    console.log('ok: quem não é da corrida não envia');
    await assert.rejects(enviar('p', 'fechado'), /encerrado/);
    console.log('ok: chat fechado (1 h depois do fim) não envia');
    await assert.rejects(enviar('p', 'c1', ''), /vazia/);
    console.log('ok: mensagem vazia recusada');
    console.log('chat.test.js: tudo ok');
}

main().then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(1); });
