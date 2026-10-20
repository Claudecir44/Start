package com.cjstudio.start.data.repository

import com.cjstudio.start.data.model.Local
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Sugestão da busca de endereço ("Av. Ipiranga, 1000 — Porto Alegre").
data class SugestaoEndereco(val placeId: String, val principal: String, val secundario: String)

// Busca de endereço com o Google Places. Uma "sessão" (token) por busca:
// as digitações + o endereço escolhido saem como UMA cobrança no Google.
interface IEnderecoRepository {
    fun novaSessao()
    suspend fun sugerir(texto: String, perto: Local?): Result<List<SugestaoEndereco>>
    suspend fun detalhar(sugestao: SugestaoEndereco): Result<Local>
}

@Singleton
class EnderecoRepository @Inject constructor(
    private val places: Provider<PlacesClient>
) : IEnderecoRepository {

    private var sessao: AutocompleteSessionToken = AutocompleteSessionToken.newInstance()

    override fun novaSessao() {
        sessao = AutocompleteSessionToken.newInstance()
    }

    override suspend fun sugerir(texto: String, perto: Local?): Result<List<SugestaoEndereco>> = runCatching {
        if (texto.trim().length < 3) return@runCatching emptyList()
        val pedido = FindAutocompletePredictionsRequest.builder()
            .setQuery(texto.trim())
            .setSessionToken(sessao)
            .setCountries("BR")
            .apply {
                // Prioriza (sem limitar) o que está perto de quem procura.
                perto?.let { setLocationBias(CircularBounds.newInstance(LatLng(it.lat, it.lng), RAIO_PREFERENCIA_M)) }
            }
            .build()
        places.get().findAutocompletePredictions(pedido).await().autocompletePredictions.map {
            SugestaoEndereco(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString())
        }
    }

    override suspend fun detalhar(sugestao: SugestaoEndereco): Result<Local> = runCatching {
        val pedido = FetchPlaceRequest.builder(sugestao.placeId, listOf(Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS))
            .setSessionToken(sessao)
            .build()
        val lugar = places.get().fetchPlace(pedido).await().place
        val ponto = lugar.location ?: throw IllegalStateException("Endereço sem localização.")
        novaSessao()
        Local(
            endereco = listOf(sugestao.principal, sugestao.secundario).filter { it.isNotBlank() }.joinToString(" - "),
            lat = ponto.latitude,
            lng = ponto.longitude
        )
    }

    private companion object {
        const val RAIO_PREFERENCIA_M = 30_000.0
    }
}
