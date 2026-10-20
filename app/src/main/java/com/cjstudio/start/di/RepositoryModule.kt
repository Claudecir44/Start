package com.cjstudio.start.di

import com.cjstudio.start.data.repository.AdminRepository
import com.cjstudio.start.data.repository.ArquivoRepository
import com.cjstudio.start.data.repository.AuthRepository
import com.cjstudio.start.data.repository.CorridaRepository
import com.cjstudio.start.data.repository.EnderecoRepository
import com.cjstudio.start.data.repository.ICorridaRepository
import com.cjstudio.start.data.repository.IEnderecoRepository
import com.cjstudio.start.data.repository.ILocalizacaoRepository
import com.cjstudio.start.data.repository.ITarifaRepository
import com.cjstudio.start.data.repository.LocalizacaoRepository
import com.cjstudio.start.data.repository.TarifaRepository
import com.cjstudio.start.data.repository.FalhasRepository
import com.cjstudio.start.data.repository.IAdminRepository
import com.cjstudio.start.data.repository.IArquivoRepository
import com.cjstudio.start.data.repository.IAuthRepository
import com.cjstudio.start.data.repository.IFalhasRepository
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.data.repository.IPassageiroRepository
import com.cjstudio.start.data.repository.MotoristaRepository
import com.cjstudio.start.data.repository.PassageiroRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepository): IAuthRepository

    @Binds
    @Singleton
    abstract fun bindArquivoRepository(impl: ArquivoRepository): IArquivoRepository

    @Binds
    @Singleton
    abstract fun bindPassageiroRepository(impl: PassageiroRepository): IPassageiroRepository

    @Binds
    @Singleton
    abstract fun bindMotoristaRepository(impl: MotoristaRepository): IMotoristaRepository

    @Binds
    @Singleton
    abstract fun bindAdminRepository(impl: AdminRepository): IAdminRepository

    @Binds
    @Singleton
    abstract fun bindFalhasRepository(impl: FalhasRepository): IFalhasRepository

    @Binds
    @Singleton
    abstract fun bindCorridaRepository(impl: CorridaRepository): ICorridaRepository

    @Binds
    @Singleton
    abstract fun bindTarifaRepository(impl: TarifaRepository): ITarifaRepository

    @Binds
    @Singleton
    abstract fun bindLocalizacaoRepository(impl: LocalizacaoRepository): ILocalizacaoRepository

    @Binds
    @Singleton
    abstract fun bindEnderecoRepository(impl: EnderecoRepository): IEnderecoRepository
}
