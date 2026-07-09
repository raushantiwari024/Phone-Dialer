package com.raushan.phone

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle results if needed
    }

    private var isDefaultState by mutableStateOf(false)
    private var bypassOnboarding by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        defaultDialerManager = DefaultDialerManager(this)
        isDefaultState = defaultDialerManager.isDefaultDialer()

        enableEdgeToEdge()
        setContent {
            PhoneTheme {
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.WRITE_CALL_LOG,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.READ_PHONE_STATE
                        )
                    )
                }

                if (isDefaultState || bypassOnboarding) {
                    MainScreen()
                } else {
                    SetDefaultDialerScreen(
                        onRequestDefault = {
                            defaultDialerManager.createRequestRoleIntent()?.let {
                                roleRequestLauncher.launch(it)
                            }
                        },
                        onLaterClick = {
                            bypassOnboarding = true
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
