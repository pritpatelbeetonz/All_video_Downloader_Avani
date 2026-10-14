package app.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import app.messages.core.prefs.ThemeMode
import app.messages.data.applock.AppLockController
import app.messages.ui.applock.LockScreen
import app.messages.ui.nav.MessagesNavHost
import app.messages.ui.nav.Routes
import app.messages.ui.theme.MessagesTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Composable root. There is no intro/onboarding surface — setup (home role, then
 * language) is handled by the activity flow that runs ahead of this, so the app
 * always starts on the conversation list.
 *
 * [openThread] carries a thread id from a notification tap; when present we
 * navigate straight to that thread.
 */
@Composable
fun MessagesApp(
    openThread: StateFlow<String?> = MutableStateFlow(null),
    onThreadOpened: () -> Unit = {},
    /** A route to push once on entry — used by the launcher's Settings tile. */
    openRoute: StateFlow<String?> = MutableStateFlow(null),
    onRouteOpened: () -> Unit = {},
    appVm: AppViewModel = hiltViewModel(),
) {
    // This tree is hosted both by MainActivity and by a page of the launcher's
    // pager. Only the pager page is resumed when the user swipes back to it, so
    // the role re-read has to hang off *this* lifecycle rather than an activity's
    // — otherwise the "set as default" card survives a granted role.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) appVm.refreshRoleState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val themeMode by appVm.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MessagesTheme(darkTheme = darkTheme) {
        val lock by appVm.lockState.collectAsStateWithLifecycle()
        when {
            lock == AppLockController.LockUiState.Loading -> SplashSurface()
            else -> {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    val nav = rememberNavController()
                    MessagesNavHost(navController = nav, startDestination = Routes.Conversations)

                    val pendingThread by openThread.collectAsStateWithLifecycle()
                    androidx.compose.runtime.LaunchedEffect(pendingThread) {
                        val id = pendingThread
                        if (id != null) {
                            // Land on the thread with the list underneath, however
                            // deep the user already was. Without this, entering
                            // repeatedly (notification taps, or the after-call
                            // Message action) stacks duplicate thread screens and
                            // back has to walk through all of them.
                            nav.navigate(Routes.thread(id)) {
                                popUpTo(Routes.Conversations) { inclusive = false }
                                launchSingleTop = true
                            }
                            onThreadOpened()
                        }
                    }

                    val pendingRoute by openRoute.collectAsStateWithLifecycle()
                    androidx.compose.runtime.LaunchedEffect(pendingRoute) {
                        val route = pendingRoute
                        if (route != null) {
                            nav.navigate(route) {
                                popUpTo(Routes.Conversations) { inclusive = false }
                                launchSingleTop = true
                            }
                            onRouteOpened()
                        }
                    }

                    // Opaque lock overlay — covers the list until unlocked (§F16).
                    if (lock == AppLockController.LockUiState.Locked) {
                        LockScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashSurface() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    )
}
