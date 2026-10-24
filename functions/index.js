// Cloud Functions do Start (passageiro, motorista e admin no mesmo projeto
// Firebase: start-3835f): cadastro de administradores e o ciclo da corrida
// (estimar, pedir, aceitar, embarcar, finalizar, cancelar). O resto
// (aprovar motorista, bloquear passageiro) é gravação direta, protegida por
// firestore.rules.
require('dotenv').config();

const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { setGlobalOptions } = require('firebase-functions/v2');
const admin = require('firebase-admin');

admin.initializeApp();

// Mesma região usada no app (FirebaseModule.provideFirebaseFunctions).
// Conta de execução: a do SDK Admin do Firebase. A conta padrão do Compute
// nasce SEM papéis neste projeto (a organização do Google Cloud desliga as
// concessões automáticas), e a do Firebase já tem Firestore/Auth/Storage.
setGlobalOptions({
    region: 'southamerica-east1',
    maxInstances: 10,
    serviceAccount: 'firebase-adminsdk-fbsvc@start-3835f.iam.gserviceaccount.com',
});

// Senha exigida pra criar administradores — só existe aqui (functions/.env),
// nunca no app.
const ADMIN_MASTER_PASSWORD = process.env.ADMIN_MASTER_PASSWORD || '';

// Precisa bater com Admin.CHAVES_PERMISSOES no app.
const CHAVES_PERMISSOES = ['motoristas', 'passageiros', 'corridas', 'financeiro', 'administradores'];

// Admin master, identificado pelo CPF (o mesmo do Match/Caronas): acesso
// total sempre, independente do mapa de permissões.
const CPF_ADMIN_MASTER = '56413025034';

function ehMaster(dadosAdmin) {
    return !!dadosAdmin && dadosAdmin.cpf === CPF_ADMIN_MASTER;
}

function sanitizarPermissoes(permissoes) {
    const limpo = {};
    for (const chave of CHAVES_PERMISSOES) {
        limpo[chave] = !!permissoes && permissoes[chave] === true;
    }
    return limpo;
}

function temPermissao(dadosAdmin, chave) {
    if (ehMaster(dadosAdmin)) return true;
    if (!dadosAdmin || !dadosAdmin.permissoes) return true; // sem mapa = acesso total
    return dadosAdmin.permissoes[chave] === true;
}

async function exigirPermissao(uid, chave) {
    const doc = await admin.firestore().collection('admins').doc(uid).get();
    if (!doc.exists) throw new HttpsError('permission-denied', 'Acesso restrito a administradores.');
    if (!temPermissao(doc.data(), chave)) throw new HttpsError('permission-denied', 'Você não tem permissão para esta ação.');
    return doc.data();
}

function cpfValido(cpf) {
    if (!/^\d{11}$/.test(cpf) || /^(\d)\1{10}$/.test(cpf)) return false;
    for (const tamanho of [9, 10]) {
        let soma = 0;
        for (let i = 0; i < tamanho; i++) soma += Number(cpf[i]) * (tamanho + 1 - i);
        if (((soma * 10) % 11) % 10 !== Number(cpf[tamanho])) return false;
    }
    return true;
}

// Chave web do projeto (a mesma que vai dentro do app, não é secreta).
const WEB_API_KEY = process.env.WEB_API_KEY || '';

// Confere a senha de uma conta existente pelo mesmo serviço de login que o
// app usa (Identity Toolkit). O Admin SDK não tem como validar senha.
async function senhaDaContaConfere(email, senha) {
    if (!WEB_API_KEY) return false;
    try {
        const resposta = await fetch(
            `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${WEB_API_KEY}`,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password: senha, returnSecureToken: false }),
            }
        );
        return resposta.ok;
    } catch (e) {
        return false;
    }
}

async function registrarLog(acao, uidAutor, detalhes) {
    try {
        await admin.firestore().collection('logsAdministracao').add({
            acao,
            uidAutor: uidAutor || null,
            detalhes: detalhes || null,
            criadoEm: admin.firestore.FieldValue.serverTimestamp(),
        });
    } catch (e) {
        console.warn('Falha ao registrar log de administração:', e.message);
    }
}

// Cria um administrador/colaborador. Trava dupla, igual ao Caronas:
// 1) senha do admin master sempre;
// 2) a partir do segundo admin, quem chama precisa estar logado como admin
//    com a permissão "administradores" (o primeiro de todos é criado na
//    tela de login, sem sessão, só com a senha master).
// A conta já nasce com e-mail confirmado: quem autorizou foi o master.
exports.cadastrarAdmin = onCall(async (request) => {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new HttpsError('failed-precondition', 'Cadastro de administrador não configurado no servidor.');
    }

    const dados = request.data || {};
    const nome = String(dados.nome || '').trim();
    const sobrenome = String(dados.sobrenome || '').trim();
    const email = String(dados.email || '').trim().toLowerCase();
    const telefone = String(dados.telefone || '').trim();
    const cpf = String(dados.cpf || '').replace(/\D/g, '');
    const senha = String(dados.senha || '');
    const role = dados.role === 'colaborador' ? 'colaborador' : 'admin';
    const permissoes = role === 'colaborador'
        ? sanitizarPermissoes(dados.permissoes)
        : sanitizarPermissoes(Object.fromEntries(CHAVES_PERMISSOES.map((c) => [c, true])));

    if (!nome || !sobrenome || !email || !telefone || !cpf || !senha) {
        throw new HttpsError('invalid-argument', 'Preencha todos os campos.');
    }
    if (!cpfValido(cpf)) throw new HttpsError('invalid-argument', 'CPF inválido.');
    if (senha.length < 6) throw new HttpsError('invalid-argument', 'A senha precisa ter pelo menos 6 caracteres.');
    if (dados.senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    const db = admin.firestore();
    const jaExisteAdmin = !(await db.collection('admins').limit(1).get()).empty;
    const uidChamador = request.auth && request.auth.uid;
    if (jaExisteAdmin) {
        if (!uidChamador) {
            throw new HttpsError('unauthenticated', 'Entre como administrador para cadastrar outro administrador.');
        }
        await exigirPermissao(uidChamador, 'administradores');
    }

    const mesmoCpf = await db.collection('admins').where('cpf', '==', cpf).limit(1).get();
    if (!mesmoCpf.empty) throw new HttpsError('already-exists', 'Já existe um administrador com este CPF.');

    let uid;
    try {
        uid = (await admin.auth().createUser({
            email,
            password: senha,
            displayName: `${nome} ${sobrenome}`,
            emailVerified: true,
        })).uid;
    } catch (error) {
        if (error.code !== 'auth/email-already-exists') {
            console.error('Erro ao criar conta de admin:', error);
            throw new HttpsError('internal', 'Erro ao criar a conta: ' + (error.message || error.code));
        }
        // Uma pessoa = uma conta pra tudo: quem já é motorista/passageiro
        // pode virar admin com a MESMA conta, desde que digite a senha dela
        // (nunca trocamos a senha de uma conta existente aqui).
        if (!(await senhaDaContaConfere(email, senha))) {
            throw new HttpsError('already-exists',
                'Este e-mail já tem conta no Start (motorista ou passageiro). Para usar a mesma conta, digite a senha dela.');
        }
        uid = (await admin.auth().getUserByEmail(email)).uid;
        if ((await db.collection('admins').doc(uid).get()).exists) {
            throw new HttpsError('already-exists', 'Esta conta já é administradora.');
        }
        // Quem autorizou foi o master: a conta passa a valer como confirmada.
        await admin.auth().updateUser(uid, { emailVerified: true });
    }

    await db.collection('admins').doc(uid).set({
        nome,
        sobrenome,
        email,
        telefone,
        cpf,
        role,
        permissoes,
        criadoEm: admin.firestore.FieldValue.serverTimestamp(),
    });
    await registrarLog('cadastrarAdmin', uidChamador, { uid, email, role });
    return { uid };
});

// Ciclo da corrida (estimar, pedir, aceitar, embarcar, finalizar, cancelar).
// Carregado depois do setGlobalOptions pra herdar região e conta de execução.
const corridas = require("./corridas");
exports.estimarCorrida = corridas.estimarCorrida;
exports.pedirCorrida = corridas.pedirCorrida;
exports.aceitarCorrida = corridas.aceitarCorrida;
exports.acaoCorrida = corridas.acaoCorrida;
exports.adicionarParada = corridas.adicionarParada;
const busca = require("./busca");
exports.avancarBusca = busca.avancarBusca;
exports.responderBusca = busca.responderBusca;
exports.enviarMensagemChat = require("./chat").enviarMensagemChat;
