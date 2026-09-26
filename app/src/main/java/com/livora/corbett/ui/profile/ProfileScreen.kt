@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.BuildConfig
import com.livora.corbett.data.local.AppPrefs
import com.livora.corbett.data.local.Session
import com.livora.corbett.data.repo.AuthRepository
import com.livora.corbett.ui.AppViewModel
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.AppTopBar
import com.livora.corbett.ui.components.ConfirmDialog
import com.livora.corbett.ui.components.GhostButton
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.InlineError
import com.livora.corbett.ui.components.LocalSnackbar
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.theme.Gold
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Intents
import com.livora.corbett.util.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val prefs: AppPrefs,
) : ViewModel() {
    data class Ui(val busy: Boolean = false, val message: String? = null, val error: String? = null, val pwError: String? = null, val pwDone: Boolean = false)

    val session: StateFlow<Session?> = auth.session
    val notifications: StateFlow<Boolean> = prefs.notificationsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun consumeMessage() = _ui.update { it.copy(message = null) }
    fun resetPw() = _ui.update { it.copy(pwError = null, pwDone = false) }

    fun setNotifications(on: Boolean) {
        viewModelScope.launch { prefs.setNotificationsEnabled(on) }
    }

    fun save(first: String, last: String, phone: String) {
        if (first.isBlank()) { _ui.update { it.copy(error = "First name can't be empty.") }; return }
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.updateProfile(first, last, phone)) {
                is ApiResult.Success -> _ui.update { it.copy(busy = false, message = "Profile updated.") }
                is ApiResult.Failure -> _ui.update { it.copy(busy = false, error = r.message) }
            }
        }
    }

    fun changePassword(current: String, next: String) {
        if (current.isEmpty()) { _ui.update { it.copy(pwError = "Enter your current password.") }; return }
        if (next.length < 8) { _ui.update { it.copy(pwError = "New password must be at least 8 characters.") }; return }
        _ui.update { it.copy(busy = true, pwError = null) }
        viewModelScope.launch {
            when (val r = auth.changePassword(current, next)) {
                is ApiResult.Success -> _ui.update { it.copy(busy = false, pwDone = true, message = r.data) }
                is ApiResult.Failure -> _ui.update { it.copy(busy = false, pwError = r.message) }
            }
        }
    }
}

/** Shared by guests and staff. [onBack] is null when shown as a tab. [onLogin] is used when signed out. */
@Composable
fun ProfileScreen(
    appVm: AppViewModel,
    onBack: (() -> Unit)?,
    onLogin: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val session by vm.session.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val notif by vm.notifications.collectAsStateWithLifecycle()
    val theme by appVm.themeMode.collectAsStateWithLifecycle()
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val snack = LocalSnackbar.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var confirmSignOut by remember { mutableStateOf(false) }
    var showPw by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotifications(granted)
    }

    LaunchedEffect(ui.message) {
        ui.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        if (onBack != null) AppTopBar("Profile", onBack) else Spacer(Modifier.statusBarsPadding())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onBack == null) Text("Profile", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
            val s = session
            if (s == null) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("You're browsing as a guest", style = MaterialTheme.typography.titleLarge)
                    Text("Sign in to manage stays, message the resort and order to your room.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    GradientButton("Sign in or create account", onLogin, Modifier.fillMaxWidth())
                }
            } else {
                var first by remember(s.user.uid) { mutableStateOf(s.user.firstName) }
                var last by remember(s.user.uid) { mutableStateOf(s.user.lastName) }
                var phone by remember(s.user.uid) { mutableStateOf(s.user.phone) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Gold.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Text(Fmt.initials(s.user.fullName), style = MaterialTheme.typography.titleLarge, color = cs.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(s.user.fullName.ifBlank { "Guest" }, style = MaterialTheme.typography.headlineSmall)
                        Text(s.user.email, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        StatusPill(Fmt.titleCase(s.user.role), Gold, Modifier.padding(top = 4.dp))
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Edit profile", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(first, { first = it }, "First name", Modifier.weight(1f))
                        AppTextField(last, { last = it }, "Last name", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    AppTextField(phone, { phone = it }, "Phone", keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone, imeAction = ImeAction.Done)
                    InlineError(ui.error)
                    Spacer(Modifier.height(10.dp))
                    GradientButton("Save changes", { vm.save(first, last, phone) }, Modifier.fillMaxWidth(), loading = ui.busy)
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Change password", { vm.resetPw(); showPw = true }, Modifier.fillMaxWidth())
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notifications", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "In-app alerts while the app is open. Push when the app is closed isn't available in this version.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = notif,
                        onCheckedChange = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setNotifications(on)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Dark", theme == "dark", { appVm.setThemeMode("dark") })
                    SelectChip("Light", theme == "light", { appVm.setThemeMode("light") })
                    SelectChip("System", theme == "system", { appVm.setThemeMode("system") })
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Text("About & contact", style = MaterialTheme.typography.titleLarge)
                Text(settings.resortName, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                settings.address?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp))
                ContactRow(Icons.Filled.Call, settings.phone) { Intents.call(ctx, settings.phone) }
                ContactRow(Icons.Filled.Chat, "WhatsApp us") { Intents.whatsapp(ctx, settings.whatsapp) }
                ContactRow(Icons.Filled.Email, settings.email) { Intents.email(ctx, settings.email, "Enquiry") }
                ContactRow(Icons.Filled.Directions, "Get directions") { Intents.openUrl(ctx, settings.mapsUrl) }
                Spacer(Modifier.height(6.dp))
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }

            if (session != null) {
                GhostButton("Sign out", { confirmSignOut = true }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.Logout)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            "Sign out?", "You'll need to sign in again to see your stays and messages.", "Sign out",
            onConfirm = { confirmSignOut = false; appVm.signOut() },
            onDismiss = { confirmSignOut = false },
            destructive = true,
        )
    }
    if (showPw) {
        PasswordDialog(ui.pwError, ui.busy, ui.pwDone, onDismiss = { showPw = false }, onSubmit = { c, n -> vm.changePassword(c, n) })
    }
}

@Composable
private fun ContactRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PasswordDialog(error: String?, busy: Boolean, done: Boolean, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var cur by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    LaunchedEffect(done) { if (done) onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(cur, { cur = it }, "Current password", password = true)
                AppTextField(next, { next = it }, "New password (min 8)", password = true, imeAction = ImeAction.Done)
                InlineError(error)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(cur, next) }, enabled = !busy) { Text(if (busy) "Saving…" else "Update") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
