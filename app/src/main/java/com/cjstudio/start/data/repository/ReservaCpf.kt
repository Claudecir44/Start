package com.cjstudio.start.data.repository

import com.cjstudio.start.data.model.CpfJaCadastradoException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// CPF único por tipo de cadastro: cpfs/{tipo}_{cpf} = { uid, tipo }. A mesma
// pessoa pode ser passageira E motorista (tipos diferentes), mas nunca ter
// dois cadastros do mesmo tipo. O documento é gravado no MESMO batch do
// perfil; firestore.rules não deixa sobrescrever um que já existe e exige
// que o perfil aponte pra um CPF reservado pelo próprio uid.
internal object ReservaCpf {
    const val COLECAO = "cpfs"

    fun idDocumento(tipo: String, cpf: String) = "${tipo}_$cpf"

    // Checagem antecipada só pra dar a mensagem certa; a trava de verdade é
    // a regra do Firestore no batch.
    suspend fun garantirLivre(db: FirebaseFirestore, tipo: String, cpf: String, uid: String) {
        val doc = db.collection(COLECAO).document(idDocumento(tipo, cpf)).get().await()
        if (doc.exists() && doc.getString("uid") != uid) throw CpfJaCadastradoException()
    }
}
