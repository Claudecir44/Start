// Teste da busca por etapas no emulador do Firestore (nada vai pro projeto
// real). Rodar da raiz do projeto:
//   npx --yes firebase-tools emulators:exec --only firestore "node functions/busca.test.js"
// A rota do Google (fetch) é simulada; os prazos são "vencidos" mexendo nos
// horários gravados, sem esperar de verdade.
process.env.GCLOUD_PROJECT = process.env.GCLOUD_PROJECT || 'start-3835f';
process.env.MAPS_SERVER_KEY = 'teste';
const assert = require('assert');
const admin = require('firebase-admin');
if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Rode dentro do emulador (FIRESTORE_EMULATOR_HOST).');
admin.initializeApp({ projectId: process.env.GCLOUD_PROJECT });

// Rota simulada: 1,5 km / 4 min pro motorista até a partida.
global.fetch = async () => ({
    ok: true,
    json: async () => ({ routes: [{ distanceMeters: 1500, duration: '240s', polyline: { encodedPolyline: 'x' } }] }),
});

const busca = require('./busca');
const db = admin.firestore();
const ORIGEM = { endereco: 'Partida', lat: -30.0, lng: -51.0 };
const KM_LAT = 1 / 111.2; // ~1 km em graus de latitude
const passado = admin.firestore.Timestamp.fromMillis(Date.now() - 1000);

async function limpar() {
    for (const col of ['corridas', 'motoristas', 'passageiros']) {
        const snap = await db.collection(col).get();
        await Promise.all(snap.docs.map((d) => d.ref.delete()));
    }
}

async function motorista(uid, km) {
    await db.collection('motoristas').doc(uid).set({
        online: true, status: 'aprovado', corridaAtualId: null,
        localizacao: { lat: ORIGEM.lat + km * KM_LAT, lng: ORIGEM.lng },
    });
}

async function novaCorrida() {
    await db.collection('passageiros').doc('p1').set({ nomeCompleto: 'Passageiro Teste' });
    const ref = db.collection('corridas').doc();
    await ref.set({
        status: 'procurando', passageiroId: 'p1', origem: ORIGEM,
        destino: { endereco: 'Destino', lat: -30.05, lng: -51.0 },
        distanciaMetros: 6000, duracaoSegundos: 900, quartoPassageiro: false, valor: 15,
    });
    await db.collection('passageiros').doc('p1').update({ corridaAtualId: ref.id });
    await busca.iniciar(ref);
    return ref;
}

const ler = async (ref) => (await ref.get()).data();
const vencer = (ref, campo) => ref.update({ ['busca.' + campo]: passado });
const responder = (ref, continuar) => busca.responderBusca.run({ auth: { uid: 'p1' }, data: { corridaId: ref.id, continuar } });

async function main() {
    await limpar();

    // --- Caminho completo: 2 km → 4 km → pergunta → novo valor → 10 km → 3 min → sem resposta
    await motorista('m1km', 1);
    await motorista('m3km', 3);
    await motorista('m8km', 8);
    let ref = await novaCorrida();
    let c = await ler(ref);
    assert.strictEqual(c.busca.etapa, 1);
    assert.strictEqual(c.busca.raioKm, 2);
    assert.deepStrictEqual(c.busca.avisados, ['m1km']);

    // Motorista a 3 km não aceita na etapa 1.
    assert.throws(() => busca.conferirAceite(c, { lat: ORIGEM.lat + 3 * KM_LAT, lng: ORIGEM.lng }), /mais perto/);
    busca.conferirAceite(c, { lat: ORIGEM.lat + 1 * KM_LAT, lng: ORIGEM.lng });

    // Prazo da etapa que ainda não venceu: nada muda.
    await busca.processar(ref);
    assert.strictEqual((await ler(ref)).busca.etapa, 1);

    await vencer(ref, 'proximaEtapaEm');
    await busca.processar(ref);
    c = await ler(ref);
    assert.strictEqual(c.busca.etapa, 2);
    assert.strictEqual(c.busca.raioKm, 4);
    assert.deepStrictEqual(c.busca.avisados, ['m1km', 'm3km']);

    await vencer(ref, 'proximaEtapaEm');
    await busca.processar(ref);
    c = await ler(ref);
    assert.strictEqual(c.busca.pergunta, 'regiao');
    assert.ok(c.busca.restanteMs > 0 && c.busca.restanteMs <= 180000);
    assert.throws(() => busca.conferirAceite(c, null), /decidindo/);

    await responder(ref, true);
    c = await ler(ref);
    assert.strictEqual(c.busca.pergunta, 'novo_valor');
    // 1,5 km do motorista + 6 km de viagem = 7,5 km → pela tarifa:
    // 4 + 1,8×7,5 + 0,3×19 = 23,20; +15% = 26,68.
    assert.strictEqual(c.busca.valorNovo, 26.68);
    assert.strictEqual(c.busca.calculoNovo.deslocamentoMetros, 1500);

    await responder(ref, true);
    c = await ler(ref);
    assert.strictEqual(c.busca.pergunta, null);
    assert.strictEqual(c.busca.etapa, 3);
    assert.strictEqual(c.busca.raioKm, 10);
    assert.strictEqual(c.valor, 26.68);
    assert.deepStrictEqual(c.busca.avisados, ['m1km', 'm3km', 'm8km']);
    assert.ok(c.busca.limiteEm.toMillis() > Date.now());

    await vencer(ref, 'limiteEm');
    await busca.processar(ref);
    c = await ler(ref);
    assert.strictEqual(c.busca.pergunta, 'sem_motorista');

    // Continuar = mais 3 minutos.
    await responder(ref, true);
    c = await ler(ref);
    assert.strictEqual(c.busca.pergunta, null);
    assert.ok(c.busca.limiteEm.toMillis() > Date.now() + 170000);

    await vencer(ref, 'limiteEm');
    await busca.processar(ref);
    await vencer(ref, 'respostaAte');
    await busca.processar(ref);
    c = await ler(ref);
    assert.strictEqual(c.status, 'cancelada');
    assert.strictEqual(c.canceladaPor, 'sistema');
    assert.strictEqual((await db.collection('passageiros').doc('p1').get()).data().corridaAtualId, null);
    console.log('ok: caminho completo 2 → 4 → novo valor → 10 km → 3 min → sem resposta encerra');

    // --- Ninguém num raio de 4 km: pergunta na hora; "Não" encerra.
    await limpar();
    await motorista('m8km', 8);
    ref = await novaCorrida();
    c = await ler(ref);
    assert.strictEqual(c.busca.pergunta, 'regiao');
    await responder(ref, false);
    c = await ler(ref);
    assert.strictEqual(c.status, 'cancelada');
    assert.strictEqual(c.canceladaPor, 'passageiro');
    assert.strictEqual(c.motivoCancelamento, 'nao_quis_continuar');
    console.log('ok: sem motorista até 4 km pergunta na hora; Não encerra');

    // --- Ninguém nem a 10 km: Sim na região vira "Motorista não disponível".
    await limpar();
    ref = await novaCorrida();
    await responder(ref, true);
    c = await ler(ref);
    assert.strictEqual(c.busca.pergunta, 'sem_motorista');
    console.log('ok: sem motorista até 10 km mostra "Motorista não disponível"');

    // --- Só motorista a 3 km: etapa 1 vazia pula direto pra 2.
    await limpar();
    await motorista('m3km', 3);
    ref = await novaCorrida();
    c = await ler(ref);
    assert.strictEqual(c.busca.etapa, 2);
    assert.deepStrictEqual(c.busca.avisados, ['m3km']);
    console.log('ok: etapa 1 vazia pula pra etapa 2');

    // --- Resposta depois do prazo: encerra e avisa.
    await vencer(ref, 'proximaEtapaEm');
    await busca.processar(ref);
    await vencer(ref, 'respostaAte');
    await assert.rejects(responder(ref, true), /tempo para responder acabou/);
    assert.strictEqual((await ler(ref)).status, 'cancelada');
    console.log('ok: responder depois do prazo encerra a busca');

    await limpar();
    console.log('busca.test.js: tudo ok');
}

main().then(() => process.exit(0)).catch((e) => {
    console.error(e);
    process.exit(1);
});
