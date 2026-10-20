package com.cjstudio.start.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.cjstudio.start.data.model.Local
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// GPS do aparelho (só com a permissão já concedida — quem pede é a tela).
interface ILocalizacaoRepository {
    fun temPermissao(): Boolean

    // Posição atual (ou a última conhecida); null sem permissão/sem sinal.
    suspend fun atual(): Local?

    // Posições em sequência enquanto alguém escuta (motorista online).
    fun acompanhar(intervaloMs: Long): Flow<Local>

    // Endereço legível de uma coordenada ("Rua X, 123 - Bairro").
    suspend fun endereco(lat: Double, lng: Double): String?
}

@Singleton
class LocalizacaoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fused: FusedLocationProviderClient
) : ILocalizacaoRepository {

    override fun temPermissao(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun atual(): Local? {
        if (!temPermissao()) return null
        val posicao = try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() ?: fused.lastLocation.await()
        } catch (_: Exception) {
            null
        } ?: return null
        return Local(lat = posicao.latitude, lng = posicao.longitude)
    }

    @SuppressLint("MissingPermission")
    override fun acompanhar(intervaloMs: Long): Flow<Local> = callbackFlow {
        if (!temPermissao()) {
            close()
            return@callbackFlow
        }
        val pedido = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervaloMs)
            .setMinUpdateIntervalMillis(intervaloMs / 2)
            .build()
        val retorno = object : LocationCallback() {
            override fun onLocationResult(resultado: LocationResult) {
                resultado.lastLocation?.let { trySend(Local(lat = it.latitude, lng = it.longitude)) }
            }
        }
        fused.requestLocationUpdates(pedido, retorno, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(retorno) }
    }

    @Suppress("DEPRECATION")
    override suspend fun endereco(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val a = Geocoder(context, Locale("pt", "BR")).getFromLocation(lat, lng, 1)?.firstOrNull() ?: return@withContext null
            listOfNotNull(
                listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(", ").ifBlank { null },
                a.subLocality,
                a.locality ?: a.subAdminArea
            ).joinToString(" - ").ifBlank { a.getAddressLine(0) }
        } catch (_: Exception) {
            null
        }
    }
}
