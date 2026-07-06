package com.raushan.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raushan.phone.telecom.DefaultDialerManager
import com.raushan.phone.ui.MainScreen
import com.raushan.phone.ui.onboarding.SetDefaultDialerScreen
import com.raushan.phone.ui.theme.PhoneTheme

class MainActivity : ComponentActivity() {

    private lateinit var defaultDialerManager: DefaultDialerManager

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // State will be re-checked in onResume
    }

    private var isDefaultState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        defaultDialerManager = DefaultDialerManager(this)
        isDefaultState = defaultDialerManager.isDefaultDialer()

        enableEdgeToEdge()
        setContent {
            PhoneTheme {
                if (isDefaultState) {
                    MainScreen()
                } else {
                    SetDefaultDialerScreen(
                        onRequestDefault = {
                            defaultDialerManager.createRequestRoleIntent()?.let {
                                roleRequestLauncher.launch(it)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isDefaultState = defaultDialerManager.isDefaultDialer()
    }
}
