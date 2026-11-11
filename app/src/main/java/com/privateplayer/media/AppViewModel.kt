package com.privateplayer.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privateplayer.domain.model.AppSettings
import com.privateplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** 根级状态：只负责主题所需的显示偏好。 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
}
