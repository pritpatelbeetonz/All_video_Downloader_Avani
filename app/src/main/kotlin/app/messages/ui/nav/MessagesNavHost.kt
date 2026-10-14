package app.messages.ui.nav

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.messages.ui.applock.AppLockSettingsScreen
import app.messages.ui.archived.ArchivedScreen
import app.messages.ui.backup.BackupScreen
import app.messages.ui.blocked.BlockedScreen
import app.messages.ui.compose.ComposeScreen
import app.messages.ui.conversations.ConversationListScreen
import app.messages.ui.premium.PaywallScreen
import app.messages.ui.scheduling.ScheduledScreen
import app.messages.ui.search.SearchScreen
import app.messages.ui.settings.NotificationSettingsScreen
import app.messages.ui.settings.SettingsScreen
import app.messages.ui.settings.PrivacyScreen
import app.messages.ui.starred.StarredScreen
import app.messages.ui.thread.ThreadScreen

@Composable
fun MessagesNavHost(
    navController: NavHostController,
    startDestination: String,
) {
    // Subtle, coordinated slide + fade (§5.1 "motion is subtle", 200–250ms). The
    // small forward slide + parallax of the outgoing screen reads as a container
    // transform for list→thread without the cost of shared-element transitions.
    val spec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(slideSpec) { it / 6 } + fadeIn(spec)
        },
        exitTransition = {
            slideOutHorizontally(slideSpec) { -it / 12 } + fadeOut(spec)
        },
        popEnterTransition = {
            slideInHorizontally(slideSpec) { -it / 12 } + fadeIn(spec)
        },
        popExitTransition = {
            slideOutHorizontally(slideSpec) { it / 6 } + fadeOut(spec)
        },
    ) {
        composable(Routes.Conversations) {
            ConversationListScreen(
                onOpenThread = { threadId -> navController.navigate(Routes.thread(threadId)) },
                onCompose = { navController.navigate(Routes.Compose) },
                onSearch = { navController.navigate(Routes.Search) },
                onSettings = { navController.navigate(Routes.Settings) },
                onPremium = { navController.navigate(Routes.Premium) },
                onArchived = { navController.navigate(Routes.Archived) },
                onBlocked = { navController.navigate(Routes.Blocked) },
                onStarred = { navController.navigate(Routes.Starred) },
                onScheduled = { navController.navigate(Routes.Scheduled) },
                onBackup = { navController.navigate(Routes.Backup) },
            )
        }
        composable(
            route = Routes.Thread,
            arguments = listOf(
                navArgument(Routes.ThreadArg) { type = NavType.StringType },
            ),
        ) { entry ->
            val threadId = entry.arguments?.getString(Routes.ThreadArg).orEmpty()
            ThreadScreen(threadId = threadId, onBack = { navController.popBackStack() })
        }
        composable(Routes.Compose) {
            ComposeScreen(
                onBack = { navController.popBackStack() },
                onOpenThread = { threadId ->
                    navController.navigate(Routes.thread(threadId)) {
                        popUpTo(Routes.Compose) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Search) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenThread = { threadId -> navController.navigate(Routes.thread(threadId)) },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPremium = { navController.navigate(Routes.Premium) },
                onAppLock = { navController.navigate(Routes.AppLock) },
                onBackup = { navController.navigate(Routes.Backup) },
                onNotificationSettings = { navController.navigate(Routes.NotificationSettings) },
                onPrivacy = { navController.navigate(Routes.Privacy) },
            )
        }
        composable(Routes.Privacy) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NotificationSettings) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AppLock) {
            AppLockSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Backup) {
            BackupScreen(
                onBack = { navController.popBackStack() },
                onPremium = { navController.navigate(Routes.Premium) },
            )
        }
        composable(Routes.Premium) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.popBackStack()
            }
        }
        composable(Routes.Archived) {
            ArchivedScreen(
                onBack = { navController.popBackStack() },
                onOpenThread = { threadId -> navController.navigate(Routes.thread(threadId)) },
            )
        }
        composable(Routes.Blocked) {
            BlockedScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Starred) {
            StarredScreen(
                onBack = { navController.popBackStack() },
                onOpenThread = { threadId -> navController.navigate(Routes.thread(threadId)) },
            )
        }
        composable(Routes.Scheduled) {
            ScheduledScreen(
                onBack = { navController.popBackStack() },
                onPremium = { navController.navigate(Routes.Premium) },
            )
        }
    }
}
