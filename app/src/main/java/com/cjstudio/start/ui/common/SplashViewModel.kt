package com.cjstudio.start.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.data.repository.IAdminRepository
import com.cjstudio.start.data.repository.IAuthRepository
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.data.repository.IPassageiroRepository
import com.cjstudio.start.util.AppTipo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Decide a primeira tela: sessão válida E perfil existente neste app (um
// motorista logado no app do passageiro sem perfil de passageiro vai pro
// login, não pra tela inicial).
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val passageiros: IPassageiroRepository,
    private val motoristas: IMotoristaRepository,
    private val admins: IAdminRepository
) : ViewModel() {

    enum class Destino { LOGIN, INICIAL }

    private val _destino = MutableStateFlow<Destino?>(null)
    val destino: StateFlow<Destino?> = _destino

    init {
        viewModelScope.launch {
            _destino.value = try {
                if (auth.sessaoValida() && temPerfil()) Destino.INICIAL else Destino.LOGIN
            } catch (_: Exception) {
                Destino.LOGIN
            }
        }
    }

    private suspend fun temPerfil(): Boolean = when {
        AppTipo.ehAdmin -> admins.souAdmin().getOrDefault(false)
        AppTipo.ehMotorista -> motoristas.buscarLogado().getOrNull() != null
        else -> passageiros.buscarLogado().getOrNull()?.let { !it.bloqueado } == true
    }
}
