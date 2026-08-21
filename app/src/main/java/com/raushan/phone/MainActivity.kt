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
import com.raushan.phone.telecom.MyInCallService
import com.raushan.phone.telecom.CallRepository
import android.telecom.Call
import com.raushan.phone.ui.mainScreen
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
        
        // Show over lock screen and wake up device for calling
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        defaultDialerManager = DefaultDialerManager(this)
        isDefaultState = defaultDialerManager.isDefaultDialer()

        enableEdgeToEdge()
        setContent {
            PhoneTheme {
                LaunchedEffect(Unit) {
                    val permissions = mutableListOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.WRITE_CALL_LOG,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_PHONE_STATE
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }

                if (isDefaultState || bypassOnboarding) {
                    mainScreen()
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

    override fun onStart() {
        super.onStart()
        isForeground = true
        MyInCallService.instance?.onActivityStateChanged(true)
    }

    override fun onStop() {
        super.onStop()
        isForeground = false
        MyInCallService.instance?.onActivityStateChanged(false)
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        isForeground = true
        isDefaultState = defaultDialerManager.isDefaultDialer()
        MyInCallService.instance?.onActivityStateChanged(true)
    }

    companion object {
        var isForeground = false
    }
}
