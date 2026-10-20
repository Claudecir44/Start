package com.cjstudio.start.data.repository

// Relatórios de erro (Crashlytics) atrás de uma interface — o resto do app
// não conhece o Crashlytics.
interface IFalhasRepository {
    fun definirChave(chave: String, valor: String)
    fun definirUsuario(uid: String?)
    fun registrar(erro: Throwable)
}
