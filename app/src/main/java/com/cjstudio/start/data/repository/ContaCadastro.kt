package com.cjstudio.start.data.repository

import com.cjstudio.start.data.model.PerfilJaExisteException
import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.tasks.await

// Conta usada por um cadastro: criada agora (nova) ou uma que a pessoa já
// tinha em outro app do Start (admin/motorista/passageiro).
data class ContaCadastro(val uid: String, val nova: Boolean)

// Como o cadastro terminou — a tela decide pra onde ir.
enum class ResultadoCadastro {
    // Conta com e-mail ainda não confirmado: avisar e voltar ao login.
    CONFIRMAR_EMAIL,
    // Conta já confirmada (ex.: a pessoa já era admin): entra direto.
    PRONTO
}

// Passos comuns aos cadastros de passageiro e motorista, pra não repetir a
// mesma lógica de conta/rollback nos dois repositórios.
internal object FluxoCadastro {

    // senha != null: cadastro com e-mail/senha (cria ou reaproveita a conta).
    // senha == null: a pessoa já entrou pelo login e só falta este perfil.
    suspend fun obterConta(auth: IAuthRepository, email: String?, senha: String?): ContaCadastro =
        if (senha != null) {
            etapa("criar conta") { auth.criarOuEntrarConta(email.orEmpty(), senha).getOrThrow() }
        } else {
            ContaCadastro(auth.uidLogado() ?: throw IllegalStateException("Sessão expirada. Entre de novo."), nova = false)
        }

    suspend fun garantirSemPerfil(perfil: DocumentReference) {
        if (etapa("conferir perfil") { perfil.get().await().exists() }) throw PerfilJaExisteException()
    }

    // Conta confirmada entra direto; senão manda o link e sai (o login só
    // libera depois da confirmação). Falha no envio não desfaz o cadastro —
    // o login reenvia.
    suspend fun concluir(auth: IAuthRepository, conta: ContaCadastro): ResultadoCadastro =
        if (auth.emailVerificado()) {
            auth.lembrarSessao(conta.uid)
            ResultadoCadastro.PRONTO
        } else {
            // Cadastro já gravado: demora no envio não pode desfazer nada.
            runCatching { etapa("enviar e-mail de confirmação") { auth.enviarVerificacaoEmail() } }
            auth.sair()
            ResultadoCadastro.CONFIRMAR_EMAIL
        }

    // Conta criada agora é apagada; conta que já existia só tem a sessão
    // encerrada (nunca apagar a conta de quem já era admin/passageiro).
    suspend fun desfazer(auth: IAuthRepository, conta: ContaCadastro?, veioDoCadastro: Boolean) {
        when {
            conta?.nova == true -> auth.excluirContaAtual()
            conta != null && veioDoCadastro -> auth.sair()
        }
    }
}
