package com.livora.corbett.ui.staff

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RoomService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livora.corbett.ui.AppViewModel
import com.livora.corbett.ui.components.AnimatedBottomBar
import com.livora.corbett.ui.components.ForestBackdrop
import com.livora.corbett.ui.components.NavItem
import com.livora.corbett.ui.navigation.StaffActions
import com.livora.corbett.ui.staff.bookings.StaffBookingsScreen
import com.livora.corbett.ui.staff.dashboard.DashboardScreen
import com.livora.corbett.ui.staff.housekeeping.HousekeepingScreen
import com.livora.corbett.ui.staff.inbox.StaffInboxScreen
import com.livora.corbett.ui.staff.requests.RequestsScreen
import com.livora.corbett.ui.theme.LocalDarkTheme
import com.livora.corbett.ui.theme.LocalReduceMotion

@Composable
fun StaffShell(appVm: AppViewModel, actions: StaffActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val badges by appVm.badges.collectAsStateWithLifecycle()
    val promptShown by appVm.staffPromptShown.collectAsStateWithLifecycle()
    val reduce = LocalReduceMotion.current
    var askNotif by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) { appVm.refreshBadges() }
    LaunchedEffect(promptShown) {
        if (promptShown == false && Build.VERSION.SDK_INT >= 33) askNotif = true
    }
    BackHandler(enabled = tab != 0) { tab = 0 }

    val items = listOf(
        NavItem("Dashboard", Icons.Filled.Dashboard),
        NavItem("Bookings", Icons.Filled.MenuBook),
        NavItem("Housekeeping", Icons.Filled.CleaningServices),
        NavItem("Requests", Icons.Filled.RoomService, badges.requests),
        NavItem("Inbox", Icons.Filled.Inbox, badges.messages),
    )

    ForestBackdrop(Modifier.fillMaxSize(), mood = if (LocalDarkTheme.current) 0.35f else 0.9f, tiltEnabled = false) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = { AnimatedBottomBar(items, tab, { tab = it }) },
        ) { pad ->
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize().padding(pad),
                transitionSpec = { fadeIn(tween(if (reduce) 0 else 220)) togetherWith fadeOut(tween(if (reduce) 0 else 120)) },
                label = "staff-tabs",
            ) { t ->
                when (t) {
                    0 -> DashboardScreen(actions, onTab = { tab = it }, onSignOut = { appVm.signOut() })
                    1 -> StaffBookingsScreen(onOpen = actions.openBooking, onWalkIn = actions.walkIn)
                    2 -> HousekeepingScreen()
                    3 -> RequestsScreen()
                    else -> StaffInboxScreen(onOpen = actions.openThread)
                }
            }
        }
    }

    if (askNotif) {
        AlertDialog(
            onDismissRequest = { askNotif = false; appVm.markStaffPromptShown() },
            title = { Text("Stay on top of guest activity") },
            text = { Text("Allow notifications to be alerted about new bookings, guest messages and room service requests.") },
            confirmButton = {
                TextButton(onClick = {
                    askNotif = false
                    appVm.markStaffPromptShown()
                    if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { askNotif = false; appVm.markStaffPromptShown() }) { Text("Not now") } },
        )
    }
}
