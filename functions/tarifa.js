// Cálculo do preço da corrida — função pura (sem Firebase), testável com
// `node functions/tarifa.test.js`. O app tem a mesma conta em
// CalculadoraTarifa.kt só pra MOSTRAR prévias; o valor que vale é este.
//
// Regras (definidas pelo dono em 20/10/2026):
//  1. Distância/tempo TOTAIS = deslocamento do motorista até o passageiro
//     + viagem da partida ao destino.
//  2. Até kmFaixaMinima (5 km) no total: o motorista recebe o mínimo fixo
//     (valorMinimoMotorista, R$ 10,00).
//  3. Acima disso: bandeirada + porKm × km + porMinuto × min (totais),
//     nunca abaixo do mínimo do motorista.
//  4. 4º passageiro (banco da frente, se o motorista aceitar): +20%.
//  5. Cada parada extra pedida durante a viagem: + valorPorParada.
//  6. Taxa da plataforma (15%) sobre tudo — o total é o que o passageiro paga
//     (mínimo: 10,00 + 15% = 11,50).

// Precisa bater com Tarifa.kt no app.
const TARIFA_PADRAO = {
    bandeirada: 4.0,
    porKm: 1.8,
    porMinuto: 0.3,
    valorMinimoMotorista: 10.0,
    kmFaixaMinima: 5.0,
    taxaPlataforma: 0.15,
    acrescimoQuartoPassageiro: 0.20,
    valorPorParada: 3.0,
    raioBuscaKm: 10.0,
};

const centavos = (v) => Math.round(v * 100) / 100;

// Devolve o "calculo" gravado na corrida (o descritivo mostrado no fim).
function calcularCorrida(tarifa, { deslocamentoMetros = 0, deslocamentoSegundos = 0, viagemMetros, viagemSegundos, quartoPassageiro = false, paradas = 0 }) {
    const t = { ...TARIFA_PADRAO, ...tarifa };
    const totalMetros = deslocamentoMetros + viagemMetros;
    const totalSegundos = deslocamentoSegundos + viagemSegundos;
    const totalKm = totalMetros / 1000;
    const totalMin = totalSegundos / 60;

    const faixaMinima = totalKm <= t.kmFaixaMinima;
    const pelaTarifa = t.bandeirada + t.porKm * totalKm + t.porMinuto * totalMin;
    const valorBase = centavos(faixaMinima ? t.valorMinimoMotorista : Math.max(pelaTarifa, t.valorMinimoMotorista));

    const acrescimoQuartoPassageiro = quartoPassageiro ? centavos(valorBase * t.acrescimoQuartoPassageiro) : 0;
    const valorParadas = centavos(paradas * t.valorPorParada);
    const valorMotorista = centavos(valorBase + acrescimoQuartoPassageiro + valorParadas);
    const taxaPlataforma = centavos(valorMotorista * t.taxaPlataforma);
    const valorTotal = centavos(valorMotorista + taxaPlataforma);

    return {
        deslocamentoMetros,
        deslocamentoSegundos,
        viagemMetros,
        viagemSegundos,
        totalMetros,
        totalSegundos,
        regra: faixaMinima ? 'minimo' : 'tarifa',
        bandeirada: t.bandeirada,
        porKm: t.porKm,
        porMinuto: t.porMinuto,
        kmFaixaMinima: t.kmFaixaMinima,
        valorBase,
        quartoPassageiro: !!quartoPassageiro,
        percentualQuartoPassageiro: t.acrescimoQuartoPassageiro,
        acrescimoQuartoPassageiro,
        paradas,
        valorPorParada: t.valorPorParada,
        valorParadas,
        valorMotorista,
        percentualPlataforma: t.taxaPlataforma,
        taxaPlataforma,
        valorTotal,
    };
}

module.exports = { TARIFA_PADRAO, calcularCorrida };
