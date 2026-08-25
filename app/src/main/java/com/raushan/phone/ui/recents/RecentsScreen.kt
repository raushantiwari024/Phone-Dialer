package com.raushan.phone.ui.recents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raushan.phone.data.models.CallLogEntry
import com.raushan.phone.ui.calllog.callLogScreen
import com.raushan.phone.ui.dialpad.DialpadScreen
import com.raushan.phone.ui.dialpad.DialpadViewModel

/**
 * The app's home screen: recent calls, with the dial pad a tap away.
 *
 * The dial pad used to be a third bottom-navigation tab. It is now an overlay on this screen instead,
 * which is what keeps back navigation predictable: opening and closing the pad adds no navigation entry,
 * so there is no way to accumulate duplicate destinations, and back from here always means "leave the
 * app" rather than "go to some other tab".
 *
 * The pad itself already renders call and contact suggestions above the keys, so opening it gives the
 * requested layout — suggestions on top, keys at the bottom — without a second list to keep in sync.
 */
@Composable
@Suppress("FunctionName")
fun recentsScreen(
    onEntryClick: (CallLogEntry) -> Unit,
    onCallClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    dialpadViewModel: DialpadViewModel = viewModel(),
) {
    // Saveable so the pad survives rotation and process death rather than snapping shut.
    var isDialpadOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        callLogScreen(
            onEntryClick = onEntryClick,
            onCallClick = onCallClick,
            onEditBeforeCall = { number ->
                // "Edit before call" used to navigate to the dial pad destination with the number as a
                // route argument. Now it prefills the pad and opens it in place.
                dialpadViewModel.onPhoneNumberChange(
                    TextFieldValue(text = number, selection = TextRange(number.length)),
                )
                isDialpadOpen = true
            },
            onSettingsClick = onSettingsClick,
            onDialpadClick = { isDialpadOpen = true },
        )

        if (isDialpadOpen) {
            // Consumes back so it closes the pad instead of leaving the screen. Registered only while
            // the pad is open, so back still exits the app from Recents itself.
            BackHandler(enabled = true) { isDialpadOpen = false }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                DialpadScreen(
                    viewModel = dialpadViewModel,
                    onCallClick = onCallClick,
                    onClose = { isDialpadOpen = false },
                )
            }
        }
    }
}
