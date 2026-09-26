package com.livora.corbett

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livora.corbett.ui.AppViewModel
import com.livora.corbett.ui.components.LocalSnackbar
import com.livora.corbett.ui.navigation.AppNavHost
import com.livora.corbett.ui.theme.CorbettTheme
import com.livora.corbett.ui.theme.LocalAssetBase
import com.livora.corbett.ui.theme.LocalReduceMotion
import com.livora.corbett.ui.theme.resolveDark
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appVm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the system splash until the persisted onboarding flag has been read (prevents a login/onboarding flash).
        splash.setKeepOnScreenCondition { appVm.onboardingSeen.value == null }
        enableEdgeToEdge()

        setContent {
            val mode by appVm.themeMode.collectAsStateWithLifecycle()
            val settings by appVm.settings.collectAsStateWithLifecycle()
            val snackbar = remember { SnackbarHostState() }
            val reduceMotion = remember {
                Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }

            LaunchedEffect(Unit) { appVm.toasts.collect { snackbar.showSnackbar(it) } }

            CompositionLocalProvider(
                LocalReduceMotion provides reduceMotion,
                LocalAssetBase provides settings.assetBaseUrl,
                LocalSnackbar provides snackbar,
            ) {
                CorbettTheme(darkTheme = resolveDark(mode)) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            AppNavHost(appVm)
                            SnackbarHost(
                                snackbar,
                                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp, start = 12.dp, end = 12.dp),
                            ) { data -> Snackbar(data) }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appVm.onForeground()
    }

    override fun onStop() {
        appVm.onBackground()
        super.onStop()
    }
}
