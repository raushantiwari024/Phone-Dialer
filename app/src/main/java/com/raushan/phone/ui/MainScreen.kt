package com.raushan.phone.ui

import android.telecom.Call
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.raushan.phone.telecom.TelecomHelper
import com.raushan.phone.ui.calllog.CallLogScreen
import com.raushan.phone.ui.contacts.ContactDetailScreen
import com.raushan.phone.ui.contacts.ContactsScreen
import com.raushan.phone.ui.dialpad.DialpadScreen
import com.raushan.phone.ui.incall.ActiveCallScreen
import com.raushan.phone.ui.incall.InCallViewModel
import com.raushan.phone.ui.incall.IncomingCallScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dialpad : Screen("dialpad", "Keypad", Icons.Default.Call)
    object CallLog : Screen("calllog", "Recents", Icons.Default.History)
    object Contacts : Screen("contacts", "Contacts", Icons.Default.Contacts)
}

@Composable
fun MainScreen(
    inCallViewModel: InCallViewModel = viewModel()
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val telecomHelper = TelecomHelper(context)
    
    val activeCall by inCallViewModel.activeCall.collectAsState()
    val callState by inCallViewModel.callState.collectAsState()
    
    val items = listOf(
        Screen.CallLog,
        Screen.Dialpad,
        Screen.Contacts
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                // Only show bottom bar on top-level screens
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val showBottomBar = items.any { it.route == currentDestination?.route }
                
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(screen.label) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dialpad.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Dialpad.route) {
                    DialpadScreen(onCallClick = { number ->
                        if (number.isNotEmpty()) {
                            telecomHelper.makeCall(number)
                        }
                    })
                }
                composable(Screen.CallLog.route) {
                    CallLogScreen(onEntryClick = { entry ->
                        telecomHelper.makeCall(entry.number)
                    })
                }
                composable(Screen.Contacts.route) {
                    ContactsScreen(onContactClick = { contact ->
                        navController.navigate("contactDetail/${contact.id}")
                    })
                }
                composable(
                    route = "contactDetail/{contactId}",
                    arguments = listOf(navArgument("contactId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val contactId = backStackEntry.arguments?.getLong("contactId") ?: 0L
                    ContactDetailScreen(
                        contactId = contactId,
                        onBackClick = { navController.popBackStack() },
                        onCallClick = { number -> telecomHelper.makeCall(number) }
                    )
                }
            }
        }

        // Call UI Overlay
        if (activeCall != null) {
            when (callState) {
                Call.STATE_RINGING -> {
                    IncomingCallScreen(viewModel = inCallViewModel)
                }
                Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_HOLDING, Call.STATE_CONNECTING -> {
                    ActiveCallScreen(viewModel = inCallViewModel)
                }
            }
        }
    }
}
