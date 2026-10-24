package com.cjstudio.start

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cjstudio.start.data.repository.INotificacaoRepository
import com.cjstudio.start.ui.chat.ChatActivity
import com.cjstudio.start.ui.chat.ChatViewModel
import com.cjstudio.start.ui.common.SplashActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// Recebe os avisos do servidor. Com o app fechado/em segundo plano quem
// mostra é o próprio Android (canal vindo na mensagem); aqui só tratamos o
// app aberto — e o token novo do aparelho.
@AndroidEntryPoint
class StartMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificacoes: INotificacaoRepository

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        escopo.launch { notificacoes.salvarToken(token) }
    }

    override fun onMessageReceived(mensagem: RemoteMessage) {
        val aviso = mensagem.notification ?: return
        val canal = aviso.channelId ?: StartApplication.CANAL_AVISOS
        // Mensagem de chat com o app aberto: tocar abre a conversa direto.
        val chatId = mensagem.data["corridaId"].takeIf { mensagem.data["tipo"] == "chat" }
        val destino = if (chatId != null) {
            Intent(this, ChatActivity::class.java).putExtra(ChatViewModel.EXTRA_CHAT_ID, chatId)
        } else {
            Intent(this, SplashActivity::class.java)
        }
        val abrir = PendingIntent.getActivity(
            this, chatId?.hashCode() ?: 0,
            destino.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificacao = NotificationCompat.Builder(this, canal)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(aviso.title)
            .setContentText(aviso.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(aviso.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notificacao) }
    }
}
