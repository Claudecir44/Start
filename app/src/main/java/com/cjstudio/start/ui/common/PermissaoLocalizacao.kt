package com.cjstudio.start.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Pede a localização (precisa ser registrado antes do onStart da tela).
// aoResponder recebe true se alguma das duas (precisa ou aproximada) foi dada.
fun AppCompatActivity.registrarPermissaoLocalizacao(aoResponder: (Boolean) -> Unit): ActivityResultLauncher<Array<String>> =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
        aoResponder(resultado.values.any { it })
    }

fun ActivityResultLauncher<Array<String>>.pedirLocalizacao() =
    launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

fun Context.temPermissaoLocalizacao(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

// Android 13+: avisos de corrida precisam da permissão de notificação.
fun AppCompatActivity.pedirNotificacaoSeNecessario(lancador: ActivityResultLauncher<String>) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    lancador.launch(Manifest.permission.POST_NOTIFICATIONS)
}

fun AppCompatActivity.registrarPermissaoNotificacao(): ActivityResultLauncher<String> =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
