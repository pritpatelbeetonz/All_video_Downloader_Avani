package app.messages.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import app.messages.R
import app.messages.monetization.ads.BigNativeAd

/**
 * Onboarding survey ("What matters most in your messaging app?", Figma 43:1429) —
 * shown right after the welcome splash. Multi-select feature cards (the selected
 * one is the brand-blue card with a white check) + a sponsored slot, then "Done".
 * Picks are persisted via [OnboardingViewModel.saveSurvey] for later personalization.
 */
private val ScreenBg = Color(0xFFF7F8FA)
private val Ink = Color(0xFF111827)
private val Brand = Color(0xFF2563EB)
private val CardBg = Color(0xFFFFFFFF)
private val CardBorder = Color(0xFFEAECF1)
private val Sub = Color(0xFF6B7280)

private data class SurveyItem(
    val id: String,
    val stringRes: Int,
    val icon: ImageVector,
    val color: Color,
)

private val SurveyItems = listOf(
    SurveyItem("pre_conversation_lock", R.string.survey_pre_conversation_lock, Icons.Rounded.Lock, Color(0xFF0057CC)),
    SurveyItem("auto_backup", R.string.survey_auto_backup, Icons.Rounded.CloudUpload, Color(0xFF3B82F6)),
    SurveyItem("pdf_export", R.string.survey_pdf_export, Icons.Rounded.PictureAsPdf, Color(0xFFEF4444)),
    SurveyItem("scheduled_messages", R.string.survey_scheduled_messages, Icons.Rounded.CalendarMonth, Color(0xFF6366F1)),
    SurveyItem("private_folder", R.string.survey_private_folder, Icons.Rounded.Folder, Color(0xFFF59E0B)),
)

@Composable
fun SurveyScreen(
    onDone: (Set<String>) -> Unit,
    vm: SurveyAdViewModel = hiltViewModel(),
) {
    val font = MaterialTheme.typography.titleMedium.fontFamily
    val nativeAd by vm.ad.collectAsStateWithLifecycle()
    // Pre-Conversation Lock pre-selected, matching the design.
    var selected by remember { mutableStateOf(setOf("pre_conversation_lock")) }

    Surface(modifier = Modifier.fillMaxSize(), color = ScreenBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            // Title + Done
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    stringResource(R.string.survey_title),
                    modifier = Modifier.weight(1f).padding(end = 12.dp, top = 4.dp),
                    fontFamily = font, fontWeight = FontWeight.W700, fontSize = 18.sp,
                    lineHeight = 23.sp, color = Ink,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brand)
                        .clickable { onDone(selected) }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text(stringResource(R.string.done), fontFamily = font, fontWeight = FontWeight.W600, fontSize = 15.sp, color = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))

            SurveyItems.forEach { item ->
                SurveyCard(
                    item = item,
                    selected = item.id in selected,
                    onToggle = {
                        selected = if (item.id in selected) selected - item.id else selected + item.id
                    },
                    font = font,
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.weight(1f))
            val ad = nativeAd
            if (ad != null) {
                BigNativeAd(ad, Modifier.fillMaxWidth())
            } else {
                AdSlot(font)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SurveyCard(
    item: SurveyItem,
    selected: Boolean,
    onToggle: () -> Unit,
    font: androidx.compose.ui.text.font.FontFamily?,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(if (selected) Brand else CardBg)
            .then(if (selected) Modifier else Modifier.border(1.dp, CardBorder, shape))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (selected) Color(0x33FFFFFF) else item.color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, null, tint = if (selected) Color.White else item.color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(14.dp))
        Text(
            stringResource(item.stringRes),
            modifier = Modifier.weight(1f),
            fontFamily = font, fontWeight = FontWeight.W600, fontSize = 16.sp,
            color = if (selected) Color.White else Ink,
        )
        if (selected) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = Brand, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Sponsored slot matching the design's native-ad card. Placeholder content for
 * now — bind to the AdsController native ad (shown to non-premium users) when the
 * ads pipeline is enabled.
 */
@Composable
private fun AdSlot(font: androidx.compose.ui.text.font.FontFamily?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFECEEF2))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Brand).padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(stringResource(R.string.ad_tag), fontFamily = font, fontWeight = FontWeight.W700, fontSize = 10.sp, color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.Info, null, tint = Brand, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.ad_tag), fontFamily = font, fontWeight = FontWeight.W800, fontSize = 22.sp, color = Ink)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White))
            Spacer(Modifier.size(10.dp))
            Column {
                Text(stringResource(R.string.ad_sponsored), fontFamily = font, fontWeight = FontWeight.W600, fontSize = 14.sp, color = Ink)
                Row {
                    repeat(4) { Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC400), modifier = Modifier.size(13.dp)) }
                    Icon(Icons.Rounded.Star, null, tint = Color(0xFFCBD0D8), modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.ad_discover_more),
            fontFamily = font, fontWeight = FontWeight.W500, fontSize = 12.sp, color = Sub,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Brand)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.ad_open), fontFamily = font, fontWeight = FontWeight.W600, fontSize = 14.sp, color = Color.White)
        }
    }
}
