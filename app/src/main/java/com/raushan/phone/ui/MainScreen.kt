package com.raushan.phone.ui

import android.telecom.Call
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.content.Intent
import com.raushan.phone.R
import com.raushan.phone.telecom.CallUiCoordinator
import com.raushan.phone.telecom.TelecomHelper
import com.raushan.phone.ui.incall.InCallActivity
import com.raushan.phone.ui.theme.PhoneTheme
import com.raushan.phone.ui.contacts.ContactDetailScreen
import com.raushan.phone.ui.contacts.ContactsScreen
import com.raushan.phone.ui.incall.InCallViewModel
import com.raushan.phone.ui.recents.recentsScreen
import com.raushan.phone.ui.settings.SettingsScreen

/**
 * The app's two top-level destinations.
 *
 * Reduced from three. "History" and "Calls" were separate tabs showing the same call data from two
 * angles, and the dial pad now lives on Recents as an overlay rather than as a tab of its own — so
 * there is one place to see recent activity and one place to see people.
 *
 * Labels are string resources rather than literals so they can be localised.
 */
sealed class Screen(val route: String, @param:StringRes val labelRes: Int, val icon: ImageVector) {
    data object Recents : Screen("recents", R.string.recents_tab, Icons.Default.History)
    data object Contacts : Screen("contacts", R.string.contacts_tab, Icons.Default.Contacts)
}

@Composable
fun mainScreen(
    inCallViewModel: InCallViewModel = viewModel(
        viewModelStoreOwner = (LocalContext.current as? ComponentActivity)
            ?: LocalViewModelStoreOwner.current!!
    )
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val telecomHelper = TelecomHelper(context)

    val inCallState by inCallViewModel.uiState.collectAsStateWithLifecycle()
    val ongoingCall = inCallState.primary?.takeIf { it.state.isOngoing }

    // The call screens no longer render here. They live in InCallActivity, which is the only surface
    // allowed to show over the keyguard. What remains is the minimised banner, which reopens it.

    val items = listOf(Screen.Recents, Screen.Contacts)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (ongoingCall != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .clickable {
                                CallUiCoordinator.clearDismissed(ongoingCall.callId)
                                context.startActivity(
                                    Intent(context, InCallActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = stringResource(R.string.active_call_content_description),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = ongoingCall.displayName.ifBlank {
                                    stringResource(R.string.ongoing_call_label)
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = ongoingCall.durationText,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            },
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                // Show bottom bar on primary screen destinations
                val showBottomBar = items.any { screen ->
                    val destRoute = currentDestination?.route ?: ""
                    destRoute.startsWith(screen.route)
                }

                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        items.forEach { screen ->
                            val isSelected = currentDestination?.hierarchy?.any {
                                val destRoute = it.route ?: ""
                                destRoute.startsWith(screen.route)
                            } == true

                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(stringResource(screen.labelRes)) },
                                selected = isSelected,
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
                // Recents is where the app opens.
                startDestination = Screen.Recents.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Recents.route) {
                    recentsScreen(
                        onEntryClick = { entry ->
                            navController.navigate("contactDetail?phoneNumber=${entry.number}")
                        },
                        onCallClick = { number ->
                            if (number.isNotEmpty()) telecomHelper.makeCall(number)
                        },
                        onSettingsClick = { navController.navigate("settings") },
                    )
                }

                composable(Screen.Contacts.route) {
                    ContactsScreen(onContactClick = { contact ->
                        navController.navigate("contactDetail?contactId=${contact.id}")
                    })
                }

                composable(
                    route = "contactDetail?contactId={contactId}&phoneNumber={phoneNumber}",
                    arguments = listOf(
                        navArgument("contactId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("phoneNumber") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val contactIdStr = backStackEntry.arguments?.getString("contactId")
                    val phoneNumber = backStackEntry.arguments?.getString("phoneNumber")
                    val contactId = contactIdStr?.toLongOrNull()

                    ContactDetailScreen(
                        contactId = contactId,
                        phoneNumber = phoneNumber,
                        onBackClick = { navController.popBackStack() },
                        onCallClick = { number -> telecomHelper.makeCall(number) }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
