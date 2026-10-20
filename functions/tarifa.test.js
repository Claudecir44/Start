// Teste rápido das regras de preço: `node functions/tarifa.test.js`.
const assert = require('assert');
const { calcularCorrida, TARIFA_PADRAO } = require('./tarifa');

// Até 5 km no total (2 km de deslocamento + 3 km de viagem): mínimo.
let c = calcularCorrida(TARIFA_PADRAO, { deslocamentoMetros: 2000, deslocamentoSegundos: 300, viagemMetros: 3000, viagemSegundos: 480 });
assert.strictEqual(c.regra, 'minimo');
assert.strictEqual(c.valorMotorista, 10);
assert.strictEqual(c.taxaPlataforma, 1.5);
assert.strictEqual(c.valorTotal, 11.5);

// Acima de 5 km: tarifa sobre o total (12 km, 25 min) = 4 + 21,60 + 7,50 = 33,10.
c = calcularCorrida(TARIFA_PADRAO, { deslocamentoMetros: 2000, deslocamentoSegundos: 300, viagemMetros: 10000, viagemSegundos: 1200 });
assert.strictEqual(c.regra, 'tarifa');
assert.strictEqual(c.valorBase, 33.1);
assert.strictEqual(c.taxaPlataforma, 4.97);
assert.strictEqual(c.valorTotal, 38.07);

// 4º passageiro (+20%) e 2 paradas (2 × 3,00) no mínimo.
c = calcularCorrida(TARIFA_PADRAO, { viagemMetros: 4000, viagemSegundos: 600, quartoPassageiro: true, paradas: 2 });
assert.strictEqual(c.acrescimoQuartoPassageiro, 2);
assert.strictEqual(c.valorParadas, 6);
assert.strictEqual(c.valorMotorista, 18);
assert.strictEqual(c.valorTotal, 20.7);

// Tarifa abaixo do mínimo nunca paga menos que R$ 10 ao motorista.
c = calcularCorrida({ ...TARIFA_PADRAO, bandeirada: 0, porKm: 0.5, porMinuto: 0 }, { viagemMetros: 6000, viagemSegundos: 600 });
assert.strictEqual(c.valorMotorista, 10);

console.log('tarifa.test.js: todas as regras ok');
