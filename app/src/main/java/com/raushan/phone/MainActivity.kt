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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.raushan.phone.telecom.CallRepository
import com.raushan.phone.telecom.CallUiCoordinator
import com.raushan.phone.telecom.DefaultDialerManager
import com.raushan.phone.telecom.InCallIntents
import com.raushan.phone.ui.mainScreen
import com.raushan.phone.ui.onboarding.SetDefaultDialerScreen
import com.raushan.phone.ui.theme.PhoneTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

        observeCallsWhileForeground()

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

    /**
     * Brings up the call screen when a call starts while our app is the foreground app.
     *
     * `repeatOnLifecycle(RESUMED)` is the whole foreground test — no usage-stats permission, no
     * polling. It is also what implements the two-tier presentation rule:
     *
     * - Our app resumed: this collector runs, so the call takes over the screen. That is a legal
     *   foreground activity start.
     * - Device locked or screen off: we are not resumed, so nothing happens here and the notification's
     *   full-screen intent takes over instead — launched by the platform, which sidesteps
     *   background-activity-start restrictions entirely.
     * - Another app in the foreground: we are not resumed either, so the app deliberately does
     *   nothing and the system heads-up call banner is the only surface. No rude takeover.
     */
    private fun observeCallsWhileForeground() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                CallRepository.state
                    .map(CallUiCoordinator::autoShowTarget)
                    .distinctUntilChanged()
                    .filterNotNull()
                    .collect { callId ->
                        startActivity(InCallIntents.callUi(this@MainActivity, callId))
                    }
            }
        }
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
