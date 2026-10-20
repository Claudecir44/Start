package com.cjstudio.start.di

import android.content.Context
import com.cjstudio.start.BuildConfig
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// GPS e busca de endereço (Google Places) — só os repositórios recebem isso.
@Module
@InstallIn(SingletonComponent::class)
object MapaModule {

    @Provides
    @Singleton
    fun provideFusedLocation(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Chave em local.properties (MAPS_API_KEY), restrita aos 3 apps no Google Cloud.
    @Provides
    @Singleton
    fun providePlacesClient(@ApplicationContext context: Context): PlacesClient {
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.MAPS_API_KEY)
        return Places.createClient(context)
    }
}
