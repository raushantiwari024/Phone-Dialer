package com.raushan.phone

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raushan.phone.telecom.DefaultDialerManager
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
    ) { _ ->
        // Handle results if needed
    }

    private var isDefaultState by mutableStateOf(false)
    private var bypassOnboarding by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setShowWhenLocked / setTurnScreenOn used to be set here unconditionally, and declared in the
        // manifest too. This is the LAUNCHER activity: it hosts the call log and the contact list, so
        // that made a user's entire call history readable over the lock screen with no call in
        // progress and no authentication. Showing over the keyguard belongs to a dedicated call
        // activity, gated on there actually being a call.

        defaultDialerManager = DefaultDialerManager(this)
        isDefaultState = defaultDialerManager.isDefaultDialer()

        // Requested here rather than from inside composition, where a recomposition could relaunch it.
        if (savedInstanceState == null) {
            requestCallPermissions()
        }

        enableEdgeToEdge()
        setContent {
            PhoneTheme {
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

    /** `launchMode="singleTop"`, so a notification tap redelivers here instead of recreating us. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isDefaultState = defaultDialerManager.isDefaultDialer()
    }

    private fun requestCallPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
