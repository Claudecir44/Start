package com.cjstudio.start.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// uid da sessão — a Splash só considera logado com Firebase Auth E este id
// salvo (mesma dupla checagem do Match/Caronas).
val KEY_UID = stringPreferencesKey("uid")

// Hora do último reenvio automático do e-mail de verificação no login. Sem
// esse intervalo, logins seguidos estouravam o limite de envio do Firebase
// (erro real visto no Caronas: TOO_MANY_ATTEMPTS_TRY_LATER).
val KEY_ULTIMO_REENVIO_VERIFICACAO = longPreferencesKey("ultimoReenvioVerificacao")

private val Context.startPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "StartPrefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideStartPrefsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.startPrefsDataStore
}
