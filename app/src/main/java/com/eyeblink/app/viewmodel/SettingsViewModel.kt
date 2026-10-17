package com.eyeblink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eyeblink.app.core.BlinkConfig
import com.eyeblink.app.core.PreferencesStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: PreferencesStore) : ViewModel() {
    val config = store.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BlinkConfig())
    fun update(transform: (BlinkConfig) -> BlinkConfig) {
        viewModelScope.launch { store.update(transform) }
    }
}
