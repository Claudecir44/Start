package com.cjstudio.start.di

import com.cjstudio.start.data.repository.AdminRepository
import com.cjstudio.start.data.repository.ArquivoRepository
import com.cjstudio.start.data.repository.AuthRepository
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
}
