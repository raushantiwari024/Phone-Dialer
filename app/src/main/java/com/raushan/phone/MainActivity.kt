package com.raushan.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.raushan.phone.telecom.DefaultDialerManager
import com.raushan.phone.ui.theme.PhoneTheme

class MainActivity : ComponentActivity() {

    private lateinit var defaultDialerManager: DefaultDialerManager

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // No-op, we'll re-check in onResume
    }

    private var isDefaultState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        defaultDialerManager = DefaultDialerManager(this)
        isDefaultState = defaultDialerManager.isDefaultDialer()

        enableEdgeToEdge()
        setContent {
            PhoneTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isDefaultState) {
                            Text(text = "This is the default dialer app!")
                            Text(text = "Check Logcat for call events.")
                        } else {
                            Button(onClick = {
                                defaultDialerManager.createRequestRoleIntent()?.let {
                                    roleRequestLauncher.launch(it)
                                }
                            }) {
                                Text(text = "Set as Default Dialer")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isDefaultState = defaultDialerManager.isDefaultDialer()
    }
}