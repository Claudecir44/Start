package com.cjstudio.start.data.model

// Situação do cadastro do motorista (Motorista.status). Os textos gravados
// no Firestore são estes — firestore.rules confere os mesmos valores.
// Todo motorista novo nasce EM_ANALISE e só pode aceitar corridas depois
// que um admin aprova os documentos (CNH, CRLV, foto).
object StatusMotorista {
    const val EM_ANALISE = "em_analise"
    const val APROVADO = "aprovado"
    const val REPROVADO = "reprovado"
    const val BLOQUEADO = "bloqueado"
}
