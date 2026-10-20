package com.cjstudio.start.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Local
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Formatação e desenho no mapa, iguais nas telas de passageiro e motorista.
object MapaUtil {

    private val moeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun valor(v: Double): String = moeda.format(v)

    fun distancia(metros: Long): String =
        if (metros < 1000) "$metros m" else String.format(Locale("pt", "BR"), "%.1f km", metros / 1000.0)

    fun duracao(segundos: Long): String {
        val minutos = (segundos + 59) / 60
        return if (minutos < 60) "$minutos min" else "${minutos / 60} h ${minutos % 60} min"
    }

    fun Local.latLng() = LatLng(lat, lng)

    // Distância em linha reta (pra filtrar corridas perto do motorista).
    fun distanciaMetros(a: Local, b: Local): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    // Mapa escuro (combina com o app) e sem botões que atrapalham.
    fun configurar(context: Context, mapa: GoogleMap) {
        runCatching { mapa.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.mapa_escuro)) }
        mapa.uiSettings.isMapToolbarEnabled = false
        mapa.uiSettings.isCompassEnabled = false
    }

    fun marcadorPartida(mapa: GoogleMap, local: Local): Marker? = mapa.addMarker(
        MarkerOptions().position(local.latLng()).title(local.endereco)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
    )

    fun marcadorDestino(mapa: GoogleMap, local: Local): Marker? = mapa.addMarker(
        MarkerOptions().position(local.latLng()).title(local.endereco)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
    )

    fun marcadorParada(mapa: GoogleMap, local: Local): Marker? = mapa.addMarker(
        MarkerOptions().position(local.latLng()).title(local.endereco)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
    )

    fun marcadorCarro(mapa: GoogleMap, local: Local): Marker? = mapa.addMarker(
        MarkerOptions().position(local.latLng())
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
            .zIndex(2f)
    )

    fun desenharRota(context: Context, mapa: GoogleMap, polyline: String?) {
        if (polyline.isNullOrBlank()) return
        mapa.addPolyline(
            PolylineOptions().addAll(PolyUtil.decode(polyline))
                .color(ContextCompat.getColor(context, R.color.amarelo))
                .width(12f)
        )
    }

    // Enquadra todos os pontos (com folga pros cartões por cima do mapa).
    fun enquadrar(mapa: GoogleMap, pontos: List<Local>, folgaPx: Int) {
        if (pontos.isEmpty()) return
        if (pontos.size == 1) {
            mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(pontos[0].latLng(), 15f))
            return
        }
        val limites = LatLngBounds.builder().apply { pontos.forEach { include(it.latLng()) } }.build()
        runCatching { mapa.animateCamera(CameraUpdateFactory.newLatLngBounds(limites, folgaPx)) }
    }


    fun ligar(context: Context, telefone: String?) {
        val numero = telefone?.filter { it.isDigit() }.orEmpty()
        if (numero.isNotEmpty()) runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero"))) }
    }
}
