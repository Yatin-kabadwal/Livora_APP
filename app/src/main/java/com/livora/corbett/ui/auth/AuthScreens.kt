@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.livora.corbett.ui.auth

import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.User
import com.livora.corbett.data.local.AppPrefs
import com.livora.corbett.data.repo.AuthRepository
import com.livora.corbett.ui.AppViewModel
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.ForestBackdrop
import com.livora.corbett.ui.components.GhostButton
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.InlineError
import com.livora.corbett.ui.components.LogoMark
import com.livora.corbett.ui.components.rememberHaptics
import com.livora.corbett.ui.theme.Forest950
import com.livora.corbett.ui.theme.ForceDark
import com.livora.corbett.ui.theme.LocalReduceMotion
import com.livora.corbett.util.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUi(val loading: Boolean = false, val error: String? = null, val info: String? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    prefs: AppPrefs,
) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui.asStateFlow()
    val lastEmail: StateFlow<String> = prefs.lastEmail.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun clear() { _ui.value = AuthUi() }

    fun login(email: String, password: String, onDone: (User) -> Unit) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { _ui.value = AuthUi(error = "Enter a valid email address."); return }
        if (password.isEmpty()) { _ui.value = AuthUi(error = "Enter your password."); return }
        _ui.value = AuthUi(loading = true)
        viewModelScope.launch {
            when (val r = auth.login(email, password)) {
                is ApiResult.Success -> { _ui.value = AuthUi(); onDone(r.data) }
                is ApiResult.Failure -> _ui.value = AuthUi(error = r.message)
            }
        }
    }

    fun register(first: String, last: String, email: String, phone: String, password: String, onDone: (User) -> Unit) {
        val err = when {
            first.isBlank() -> "Please enter your first name."
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
            phone.filter { it.isDigit() }.length < 10 -> "Enter a valid phone number."
            password.length < 8 -> "Password must be at least 8 characters."
            else -> null
        }
        if (err != null) { _ui.value = AuthUi(error = err); return }
        _ui.value = AuthUi(loading = true)
        viewModelScope.launch {
            when (val r = auth.register(first, last, email, phone, password)) {
                is ApiResult.Success -> { _ui.value = AuthUi(); onDone(r.data) }
                is ApiResult.Failure -> _ui.value = AuthUi(error = r.message)
            }
        }
    }

    fun forgot(email: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { _ui.value = AuthUi(error = "Enter a valid email address."); return }
        _ui.value = AuthUi(loading = true)
        viewModelScope.launch {
            when (val r = auth.forgotPassword(email)) {
                is ApiResult.Success -> _ui.value = AuthUi(info = r.data)
                is ApiResult.Failure -> _ui.value = AuthUi(error = r.message)
            }
        }
    }
}

// ───────────────────────── splash ─────────────────────────

@Composable
fun SplashScreen(appVm: AppViewModel, onNavigate: (String) -> Unit) {
    val seen by appVm.onboardingSeen.collectAsStateWithLifecycle()
    val reduce = LocalReduceMotion.current
    val logo = remember { Animatable(if (reduce) 1f else 0f) }
    val title = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            logo.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            title.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(seen) {
        if (seen != null) {
            delay(if (reduce) 400 else 2100)
            val s = appVm.session.value
            val dest = when {
                s != null && s.user.isStaff -> "staff"
                s != null -> "guest"
                seen == false -> "onboarding"
                else -> "login"
            }
            onNavigate(dest)
        }
    }
    ForceDark {
        Box(Modifier.fillMaxSize().background(Forest950)) {
            ForestBackdrop(Modifier.fillMaxSize(), mood = 0.75f)
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LogoMark(
                    Modifier.graphicsLayer {
                        alpha = logo.value
                        scaleX = 0.6f + 0.4f * logo.value
                        scaleY = 0.6f + 0.4f * logo.value
                    },
                    size = 96.dp,
                )
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.graphicsLayer { alpha = title.value; translationY = (1f - title.value) * 24f },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Corbett The Vedant", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                    Text("BY LIVORA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp)
                }
            }
        }
    }
}

// ───────────────────────── onboarding ─────────────────────────

private data class OnboardPage(val title: String, val body: String)

private val onboardPages = listOf(
    OnboardPage("Wake up in the wild", "Ten thoughtfully designed rooms at the edge of Jim Corbett, where mornings begin with birdsong and mist."),
    OnboardPage("Book in a few taps", "See live availability, pick your dates and reserve. You pay at the property, with free cancellation before your stay."),
    OnboardPage("Your stay, at your fingertips", "Message the resort, order to your room and follow every request, all from one place."),
)

@Composable
fun OnboardingScreen(appVm: AppViewModel, onFinish: () -> Unit) {
    val pager = rememberPagerState(pageCount = { onboardPages.size })
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val finish = { appVm.markOnboardingSeen(); onFinish() }
    ForceDark {
        Box(Modifier.fillMaxSize().background(Forest950)) {
            ForestBackdrop(
                Modifier.fillMaxSize(),
                mood = 0.5f + 0.25f * pager.currentPage,
                scroll = { pager.currentPage + pager.currentPageOffsetFraction },
            )
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { finish() }) { Text("Skip", color = MaterialTheme.colorScheme.onBackground) }
                }
                Spacer(Modifier.weight(1f))
                HorizontalPager(pager, Modifier.fillMaxWidth()) { page ->
                    val p = onboardPages[page]
                    Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                        Text(p.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(28.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onboardPages.indices.forEach { i ->
                            val active = i == pager.currentPage
                            val w by animateDpAsState(if (active) 26.dp else 8.dp, spring(dampingRatio = 0.6f), label = "dot-w")
                            val c by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), label = "dot-c")
                            Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
                        }
                    }
                    val last = pager.currentPage == onboardPages.lastIndex
                    GradientButton(
                        if (last) "Get started" else "Next",
                        onClick = {
                            if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                            haptics.tap()
                        },
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ───────────────────────── shared frame ─────────────────────────

@Composable
private fun AuthFrame(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    ForceDark {
        Box(Modifier.fillMaxSize().background(Forest950)) {
            ForestBackdrop(Modifier.fillMaxSize(), mood = 0.8f)
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground) }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
                Spacer(Modifier.height(8.dp))
                LogoMark(size = 56.dp)
                Spacer(Modifier.height(10.dp))
                Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ───────────────────────── login ─────────────────────────

@Composable
fun LoginScreen(
    onLoggedIn: (User) -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
    onGuest: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val last by vm.lastEmail.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    LaunchedEffect(last) { if (email.isBlank() && last.isNotBlank()) email = last }
    LaunchedEffect(Unit) { vm.clear() }

    val submit = { focus.clearFocus(); vm.login(email, password, onLoggedIn) }
    AuthFrame("Welcome back", "Sign in as a guest or as resort staff.", null) {
        AppTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email, leading = Icons.Filled.Email)
        AppTextField(
            password, { password = it }, "Password",
            password = true, imeAction = ImeAction.Done, leading = Icons.Filled.Lock,
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        InlineError(ui.error)
        GradientButton("Sign in", { submit() }, Modifier.fillMaxWidth(), loading = ui.loading)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onForgot) { Text("Forgot password?", color = MaterialTheme.colorScheme.primary) }
            TextButton(onClick = onRegister) { Text("Create account", color = MaterialTheme.colorScheme.primary) }
        }
        GhostButton("Continue as guest", onGuest, Modifier.fillMaxWidth())
    }
}

// ───────────────────────── register ─────────────────────────

@Composable
fun RegisterScreen(onBack: () -> Unit, onRegistered: (User) -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { vm.clear() }
    val submit = { focus.clearFocus(); vm.register(first, last, email, phone, password, onRegistered) }

    AuthFrame("Create your account", "Manage stays, message the resort and order to your room.", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(first, { first = it }, "First name", Modifier.weight(1f), leading = Icons.Filled.Person)
            AppTextField(last, { last = it }, "Last name", Modifier.weight(1f))
        }
        AppTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email, leading = Icons.Filled.Email)
        AppTextField(phone, { phone = it }, "Phone", keyboardType = KeyboardType.Phone, leading = Icons.Filled.Phone)
        AppTextField(
            password, { password = it }, "Password (min 8 characters)",
            password = true, imeAction = ImeAction.Done, leading = Icons.Filled.Lock,
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        InlineError(ui.error)
        GradientButton("Create account", { submit() }, Modifier.fillMaxWidth(), loading = ui.loading)
    }
}

// ───────────────────────── forgot password ─────────────────────────

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.clear() }
    AuthFrame("Reset password", "We'll email you a link to choose a new password.", onBack) {
        AppTextField(
            email, { email = it }, "Email",
            keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, leading = Icons.Filled.Email,
            keyboardActions = KeyboardActions(onDone = { vm.forgot(email) }),
        )
        InlineError(ui.error)
        AnimatedContent(ui.info, transitionSpec = { fadeIn() + slideInVertically { it / 3 } togetherWith fadeOut() }, label = "forgot-info") { info ->
            if (info != null) {
                Text(info, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            } else {
                Spacer(Modifier.size(1.dp))
            }
        }
        GradientButton("Send reset link", { vm.forgot(email) }, Modifier.fillMaxWidth(), loading = ui.loading)
        Text(
            "Resort staff: please reset your password from the admin website.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
