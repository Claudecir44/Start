// Peças usadas por corridas.js e busca.js: situações da corrida, leitura de
// dados do app, tarifa, rota de carro e distância em linha reta.
const { HttpsError } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');
const { TARIFA_PADRAO } = require('./tarifa');

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

// Linha reta (Haversine), em metros.
function distanciaMetros(a, b) {
    const r = 6371000;
    const rad = (g) => (g * Math.PI) / 180;
    const dLat = rad(b.lat - a.lat);
    const dLng = rad(b.lng - a.lng);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
    return 2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

function m2(v) {
    return 'R$ ' + v.toFixed(2).replace('.', ',');
}

module.exports = { STATUS, ATIVAS, exigirLogin, lerLocal, lerTarifa, calcularRota, distanciaMetros, m2 };
