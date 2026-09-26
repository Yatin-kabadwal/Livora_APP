package com.livora.corbett.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.livora.corbett.data.api.User
import com.livora.corbett.ui.AppViewModel
import com.livora.corbett.ui.auth.ForgotPasswordScreen
import com.livora.corbett.ui.auth.LoginScreen
import com.livora.corbett.ui.auth.OnboardingScreen
import com.livora.corbett.ui.auth.RegisterScreen
import com.livora.corbett.ui.auth.SplashScreen
import com.livora.corbett.ui.guest.GuestShell
import com.livora.corbett.ui.guest.booking.BookingScreen
import com.livora.corbett.ui.guest.booking.ConfirmationScreen
import com.livora.corbett.ui.guest.dining.DiningScreen
import com.livora.corbett.ui.guest.messages.ChatScreen
import com.livora.corbett.ui.guest.messages.ComposeMessageScreen
import com.livora.corbett.ui.guest.roomservice.RoomServiceScreen
import com.livora.corbett.ui.guest.rooms.RoomDetailScreen
import com.livora.corbett.ui.guest.stays.StayDetailScreen
import com.livora.corbett.ui.profile.ProfileScreen
import com.livora.corbett.ui.staff.StaffShell
import com.livora.corbett.ui.staff.bookings.StaffBookingScreen
import com.livora.corbett.ui.staff.bookings.WalkInScreen
import com.livora.corbett.ui.staff.inbox.StaffThreadScreen
import com.livora.corbett.ui.theme.LocalReduceMotion

private fun homeFor(user: User) = if (user.isStaff) Routes.STAFF else Routes.GUEST

private fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
fun AppNavHost(appVm: AppViewModel) {
    val nav = rememberNavController()
    val session by appVm.session.collectAsStateWithLifecycle()
    val reduce = LocalReduceMotion.current
    val ms = if (reduce) 0 else 320

    // Any transition from signed-in to signed-out (manual sign out or refresh-token failure) returns to login.
    val hadSession = remember { booleanArrayOf(session != null) }
    LaunchedEffect(session == null) {
        val now = session != null
        if (hadSession[0] && !now) nav.resetTo(Routes.LOGIN)
        hadSession[0] = now
    }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        { fadeIn(tween(ms)) + scaleIn(tween(ms), initialScale = 0.96f) }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        { fadeOut(tween(ms / 2)) }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        { fadeIn(tween(ms)) }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        { fadeOut(tween(ms / 2)) + scaleOut(tween(ms), targetScale = 0.96f) }

    val guestActions = remember(nav) {
        GuestActions(
            openRoom = { nav.navigate(Routes.room(it)) },
            openStay = { nav.navigate(Routes.stay(id = it)) },
            openLookup = { ref, email -> nav.navigate(Routes.stay(ref = ref, email = email)) },
            openChat = { nav.navigate(Routes.chat(it)) },
            compose = { nav.navigate(Routes.COMPOSE) },
            dining = { nav.navigate(Routes.DINING) },
            login = { nav.navigate(Routes.LOGIN) { launchSingleTop = true } },
            roomService = { nav.navigate(Routes.roomService(it)) },
        )
    }
    val staffActions = remember(nav) {
        StaffActions(
            openBooking = { nav.navigate(Routes.sBooking(it)) },
            openThread = { nav.navigate(Routes.sThread(it)) },
            walkIn = { nav.navigate(Routes.S_WALKIN) },
            profile = { nav.navigate(Routes.PROFILE) },
        )
    }
    val stringArg = { name: String -> navArgument(name) { type = NavType.StringType } }
    val optArg = { name: String -> navArgument(name) { type = NavType.StringType; defaultValue = "" } }

    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(appVm) { dest ->
                nav.navigate(
                    when (dest) {
                        "staff" -> Routes.STAFF
                        "guest" -> Routes.GUEST
                        "onboarding" -> Routes.ONBOARDING
                        else -> Routes.LOGIN
                    },
                ) { popUpTo(Routes.SPLASH) { inclusive = true } }
            }
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(appVm) { nav.navigate(Routes.LOGIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = { nav.resetTo(homeFor(it)) },
                onRegister = { nav.navigate(Routes.REGISTER) },
                onForgot = { nav.navigate(Routes.FORGOT) },
                onGuest = {
                    // If a guest shell is already underneath (opened login from Profile), just go back to it.
                    if (!nav.popBackStack(Routes.GUEST, false)) {
                        nav.navigate(Routes.GUEST) { popUpTo(Routes.LOGIN) { inclusive = true } }
                    }
                },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(onBack = { nav.popBackStack() }, onRegistered = { nav.resetTo(homeFor(it)) })
        }
        composable(Routes.FORGOT) { ForgotPasswordScreen(onBack = { nav.popBackStack() }) }

        composable(Routes.GUEST) {
            GuestShell(appVm, guestActions, onLogin = { guestActions.login() })
        }
        composable(Routes.STAFF) { StaffShell(appVm, staffActions) }

        composable(Routes.ROOM, arguments = listOf(stringArg("slug"))) {
            RoomDetailScreen(onBack = { nav.popBackStack() }, onBook = { nav.navigate(Routes.booking(it)) })
        }
        composable(Routes.BOOKING, arguments = listOf(stringArg("slug"))) {
            BookingScreen(
                onBack = { nav.popBackStack() },
                onConfirmed = {
                    nav.navigate(Routes.CONFIRMATION) {
                        popUpTo(Routes.GUEST) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.CONFIRMATION) {
            ConfirmationScreen(
                onDone = { nav.popBackStack(Routes.GUEST, false) },
                onViewStays = {
                    val b = appVm.lastBooking.booking.value
                    val loggedIn = appVm.session.value != null
                    nav.navigate(
                        when {
                            b == null -> Routes.stay()
                            loggedIn && b.id.isNotBlank() -> Routes.stay(id = b.id)
                            else -> Routes.stay(ref = b.bookingRef, email = b.guestEmail)
                        },
                    ) { popUpTo(Routes.GUEST) { inclusive = false } }
                },
            )
        }
        composable(Routes.STAY, arguments = listOf(optArg("id"), optArg("ref"), optArg("email"))) {
            StayDetailScreen(onBack = { nav.popBackStack() }, actions = guestActions)
        }
        composable(Routes.ROOM_SERVICE, arguments = listOf(stringArg("bookingId"))) {
            RoomServiceScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.COMPOSE) {
            ComposeMessageScreen(
                onBack = { nav.popBackStack() },
                onSent = { token ->
                    nav.navigate(Routes.chat(token)) { popUpTo(Routes.COMPOSE) { inclusive = true } }
                },
            )
        }
        composable(Routes.CHAT, arguments = listOf(stringArg("token"))) { ChatScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DINING) { DiningScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PROFILE) {
            ProfileScreen(appVm, onBack = { nav.popBackStack() }, onLogin = { guestActions.login() })
        }

        composable(Routes.S_BOOKING, arguments = listOf(stringArg("id"))) { StaffBookingScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.S_WALKIN) {
            WalkInScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate(Routes.sBooking(id)) { popUpTo(Routes.S_WALKIN) { inclusive = true } } },
            )
        }
        composable(Routes.S_THREAD, arguments = listOf(stringArg("id"))) { StaffThreadScreen(onBack = { nav.popBackStack() }) }
    }
}
