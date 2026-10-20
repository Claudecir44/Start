package com.cjstudio.start

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cjstudio.start.data.repository.IFalhasRepository
import com.cjstudio.start.util.AppTipo
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Ponto de entrada do Hilt.
//
// Edge-to-edge (Android 15+): com targetSdk 35+ toda Activity desenha por
// baixo das barras do sistema. O espaço delas é reservado UMA vez aqui, no
// container raiz de toda Activity (mesmo padrão do Match/Caronas); o teclado
// é tratado à parte, por tela, em View.ajustarPaddingParaTeclado.
@HiltAndroidApp
class StartApplication : Application() {

    @Inject
    lateinit var falhas: IFalhasRepository

    override fun onCreate() {
        super.onCreate()

        // Qual dos três apps e se é build de teste — pra filtrar no Crashlytics.
        falhas.definirChave("tipo", AppTipo.atual)
        falhas.definirChave("debug", BuildConfig.DEBUG.toString())

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val raiz = activity.findViewById<View>(android.R.id.content) ?: return
                ViewCompat.setOnApplyWindowInsetsListener(raiz) { view, insets ->
                    val reservado = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                    )
                    view.setPadding(reservado.left, reservado.top, reservado.right, reservado.bottom)
                    insets
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
