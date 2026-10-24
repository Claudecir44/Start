// Chat entre passageiro e motorista de uma corrida (chats/{corridaId},
// mensagens em chats/{corridaId}/mensagens). Mesmas funções do chat do
// Match/Caronas, só com TEXTO (pedido do dono): lida/não lida, contador de
// não lidas, apagar pra mim/pra todos e aviso por push.
//  - Abre no aceite (aceitarCorrida cria o documento).
//  - Fecha 1 hora depois de a corrida terminar (finalizada ou cancelada):
//    acaoCorrida grava fechaEm. Depois disso fica só pra leitura dos dois.
// Enviar passa por aqui (callable) e não por gatilho do Firestore: gatilho
// precisa do Eventarc, e a organização do Google Cloud deste projeto não
// dá a permissão ao agente dele. Ler, marcar lida e apagar o app faz direto
// (firestore.rules). O texto chega já cifrado (ChatCrypto.kt), então o push
// não mostra o conteúdo.
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');
const { exigirLogin } = require('./comum');
const notificar = require('./notificacoes');

const TAMANHO_MAXIMO = 6000; // texto cifrado (1000 letras viram ~1400)

exports.enviarMensagemChat = onCall(async (request) => {
    const uid = exigirLogin(request);
    const chatId = String((request.data && request.data.chatId) || '');
    const conteudo = String((request.data && request.data.conteudo) || '');
    if (!chatId) throw new HttpsError('invalid-argument', 'Chat inválido.');
    if (!conteudo || conteudo.length > TAMANHO_MAXIMO) throw new HttpsError('invalid-argument', 'Mensagem vazia ou grande demais.');

    const db = admin.firestore();
    const chatRef = db.collection('chats').doc(chatId);
    let chat = null;
    let paraMotorista = false;

    await db.runTransaction(async (tx) => {
        const doc = await tx.get(chatRef);
        if (!doc.exists) throw new HttpsError('not-found', 'Chat não encontrado.');
        chat = doc.data();
        if (uid !== chat.passageiroId && uid !== chat.motoristaId) throw new HttpsError('permission-denied', 'Este chat não é seu.');
        if (chat.fechaEm && chat.fechaEm.toMillis() <= Date.now()) {
            throw new HttpsError('failed-precondition', 'O chat foi encerrado (1 hora depois do fim da corrida).');
        }
        paraMotorista = uid === chat.passageiroId;
        const agora = admin.firestore.FieldValue.serverTimestamp();
        tx.create(chatRef.collection('mensagens').doc(), {
            remetenteId: uid,
            destinatarioId: paraMotorista ? chat.motoristaId : chat.passageiroId,
            conteudo,
            timestamp: agora,
            lida: false,
            deletadoParaTodos: false,
            deletadoParaRemetente: false,
            deletadoParaDestinatario: false,
        });
        tx.update(chatRef, {
            [paraMotorista ? 'naoLidasMotorista' : 'naoLidasPassageiro']: admin.firestore.FieldValue.increment(1),
            ultimaMensagemEm: agora,
        });
    });

    const remetente = (paraMotorista ? chat.passageiroNome : chat.motoristaNome) || 'Start';
    const texto = 'Nova mensagem no chat da corrida';
    if (paraMotorista) {
        await notificar.avisarMotorista(chat.motoristaId, remetente, texto, chatId, 'chat');
    } else {
        await notificar.avisarPassageiro(chat.passageiroId, remetente, texto, chatId, 'chat');
    }
    return { ok: true };
});
