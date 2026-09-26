package com.livora.corbett.ui.guest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KingBed
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livora.corbett.ui.AppViewModel
import com.livora.corbett.ui.components.AnimatedBottomBar
import com.livora.corbett.ui.components.ForestBackdrop
import com.livora.corbett.ui.components.NavItem
import com.livora.corbett.ui.guest.home.HomeScreen
import com.livora.corbett.ui.guest.messages.MessagesScreen
import com.livora.corbett.ui.guest.rooms.RoomsScreen
import com.livora.corbett.ui.guest.stays.StaysScreen
import com.livora.corbett.ui.navigation.GuestActions
import com.livora.corbett.ui.profile.ProfileScreen
import com.livora.corbett.ui.theme.LocalDarkTheme
import com.livora.corbett.ui.theme.LocalReduceMotion

private const val TAB_HOME = 0
private const val TAB_ROOMS = 1
private const val TAB_STAYS = 2
private const val TAB_MESSAGES = 3
private const val TAB_PROFILE = 4

@Composable
fun GuestShell(appVm: AppViewModel, actions: GuestActions, onLogin: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    val session by appVm.session.collectAsStateWithLifecycle()
    val badges by appVm.badges.collectAsStateWithLifecycle()
    val reduce = LocalReduceMotion.current

    LaunchedEffect(session?.user?.uid) { appVm.refreshBadges() }
    BackHandler(enabled = tab != TAB_HOME) { tab = TAB_HOME }

    val items = listOf(
        NavItem("Home", Icons.Filled.Home),
        NavItem("Rooms", Icons.Filled.KingBed),
        NavItem("My Stays", Icons.Filled.Luggage),
        NavItem("Messages", Icons.Filled.Chat, badges.messages),
        NavItem("Profile", Icons.Filled.Person),
    )

    ForestBackdrop(Modifier.fillMaxSize(), mood = if (LocalDarkTheme.current) 0.35f else 0.9f, tiltEnabled = false) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = { AnimatedBottomBar(items, tab, { tab = it }) },
        ) { pad: PaddingValues ->
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize().padding(pad),
                transitionSpec = { fadeIn(tween(if (reduce) 0 else 220)) togetherWith fadeOut(tween(if (reduce) 0 else 120)) },
                label = "guest-tabs",
            ) { t ->
                when (t) {
                    TAB_HOME -> HomeScreen(session?.user?.firstName?.takeIf { it.isNotBlank() }, actions, onSearch = { tab = TAB_ROOMS })
                    TAB_ROOMS -> RoomsScreen(onOpenRoom = actions.openRoom)
                    TAB_STAYS -> StaysScreen(loggedIn = session != null, actions = actions, onBrowseRooms = { tab = TAB_ROOMS })
                    TAB_MESSAGES -> MessagesScreen(actions)
                    else -> ProfileScreen(appVm, onBack = null, onLogin = onLogin)
                }
            }
        }
    }
}
