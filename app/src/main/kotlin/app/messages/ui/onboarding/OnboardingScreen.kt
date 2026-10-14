package app.messages.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.messages.R
import kotlinx.coroutines.launch

/**
 * Onboarding, rebuilt to the Figma "messages-and-launcher" design. The flow is a
 * pure forward sequence:
 *
 *   Splash → Survey ("What matters most") → 3 intro pages → Rate Us → app
 *
 * The default-SMS-role request fires on the final intro's "Get Started" (which
 * also advances to the Rate Us prompt); the list's "Set as default" banner is the
 * post-onboarding fallback. Colors/sizes are the design's verbatim; type is Inter.
 */
private val Ink = Color(0xFF000000)
private val InkMuted = Color(0x99000000) // rgba(0,0,0,0.6)
private val DotInactive = Color(0xFFD2D5DB)
private val Brand = Color(0xFF0057CC)

private enum class OnbStep { Survey, SetDefault, Intro }

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    SetDefaultStep(
        vm = vm,
        onContinue = {
            vm.markOnboardingSeen()
            onFinish()
        },
    )
}

/** Opens the app's Play Store listing (release package id) for a rating. */
private fun openPlayStore(context: Context) {
    val pkg = context.packageName.removeSuffix(".debug")
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

// ── Set Default inline step ──────────────────────────────────────────────────

@Composable
private fun SetDefaultStep(
    vm: OnboardingViewModel,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val font = MaterialTheme.typography.titleMedium.fontFamily
    val roleStatus by vm.roleStatus.collectAsState()
    // If already default SMS app, skip forward automatically
    LaunchedEffect(roleStatus) {
        if (roleStatus == app.messages.data.role.SmsRoleStatus.Granted) {
            onContinue()
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.make_us_your_default_app),
                fontFamily = font, fontWeight = FontWeight.W700,
                fontSize = 24.sp, color = Ink, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.make_us_default_sub),
                fontFamily = font, fontWeight = FontWeight.W500,
                fontSize = 16.sp, color = InkMuted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            androidx.compose.material3.Button(
                onClick = { vm.requestRole(context as android.app.Activity) },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Brand, contentColor = Color.White,
                ),
            ) {
                Text(stringResource(R.string.set_as_default), fontFamily = font, fontWeight = FontWeight.W700, fontSize = 18.sp)
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.TextButton(onClick = onContinue) {
                Text(stringResource(R.string.skip_for_now), color = InkMuted)
            }
        }
    }
}

// ── Feature pager ────────────────────────────────────────────────────────────

@Composable
private fun FeaturePager(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // Onboarding is a fixed light brand experience (light illustrations + Ink text),
    // matching the Survey + Rate Us screens — not theme-aware. (Using colorScheme.surface
    // here turned the page dark in dark mode, hiding the black title/body.)
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> FeaturePage(
                    illustration = R.drawable.onb_inbox,
                    title = stringResource(R.string.smart_message_inbox),
                    body = stringResource(R.string.onb_body_1),
                    primaryLabel = stringResource(R.string.continue_),
                    activeDot = 0,
                    onPrimary = { scope.launch { pagerState.animateScrollToPage(1) } },
                )
                1 -> FeaturePage(
                    illustration = R.drawable.onb_schedule,
                    title = stringResource(R.string.schedule_messages_easily),
                    body = stringResource(R.string.onb_body_2),
                    primaryLabel = stringResource(R.string.continue_),
                    activeDot = 1,
                    onPrimary = { scope.launch { pagerState.animateScrollToPage(2) } },
                )
                2 -> FeaturePage(
                    illustration = R.drawable.onb_media,
                    title = stringResource(R.string.more_than_just_texting),
                    body = stringResource(R.string.onb_body_3),
                    primaryLabel = stringResource(R.string.get_started),
                    activeDot = 2,
                    onPrimary = onComplete,
                    secondaryLabel = stringResource(R.string.maybe_later),
                    onSecondary = onSkip,
                )
            }
        }
    }
}

@Composable
private fun FeaturePage(
    illustration: Int,
    title: String,
    body: String,
    primaryLabel: String,
    activeDot: Int,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val titleFont = MaterialTheme.typography.titleMedium.fontFamily
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(illustration),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
        Spacer(Modifier.weight(1f))
        Dots(count = 3, active = activeDot)
        Spacer(Modifier.height(22.dp))
        Text(
            text = title,
            textAlign = TextAlign.Center,
            fontFamily = titleFont,
            fontWeight = FontWeight.W700,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            color = Ink,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            textAlign = TextAlign.Center,
            fontFamily = titleFont,
            fontWeight = FontWeight.W500,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            color = InkMuted,
            modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
        ) {
            Text(primaryLabel, fontFamily = titleFont, fontWeight = FontWeight.W700, fontSize = 19.sp)
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 4.dp)) {
                Text(secondaryLabel, color = InkMuted)
            }
        } else {
            Spacer(Modifier.height(20.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Dots(count: Int, active: Int) {
    Row(horizontalArrangement = Arrangement.Center) {
        for (i in 0 until count) {
            val isActive = i == active
            val width by animateDpAsState(
                targetValue = if (isActive) 22.dp else 8.dp,
                label = "dot_width",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isActive) Brand else DotInactive),
            )
        }
    }
}
