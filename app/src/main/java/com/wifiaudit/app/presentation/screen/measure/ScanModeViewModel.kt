package com.wifiaudit.app.presentation.screen.measure

import android.os.Build
import androidx.lifecycle.ViewModel
import com.wifiaudit.app.domain.model.ScanMode
import com.wifiaudit.app.domain.usecase.SetScanModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScanModeViewModel @Inject constructor(
    private val setScanModeUseCase: SetScanModeUseCase
) : ViewModel() {

    val fastModeAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun chooseFastMode()     = setScanModeUseCase(ScanMode.FAST)
    fun chooseStandardMode() = setScanModeUseCase(ScanMode.STANDARD)
}
