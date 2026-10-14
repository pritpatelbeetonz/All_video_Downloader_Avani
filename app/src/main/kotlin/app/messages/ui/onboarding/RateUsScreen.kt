package app.messages.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.messages.R
import androidx.compose.ui.graphics.Color

/**
 * "Enjoying Messages app?" rating prompt (Figma 43:1418) — the final onboarding
 * screen, shown after the third intro page. Phone mockup, five gold stars, the
 * two-tone headline, and a "Rate Us Now" button.
 */
private val Ink = Color(0xFF0F1115)
private val Brand = Color(0xFF0057CC)
private val Gold = Color(0xFFFFC400)
private val Sub = Color(0xFF6B7280)
private val ScreenBg = Color(0xFFF8F9FC)

@Composable
fun RateUsScreen(
    onRateNow: () -> Unit,
    onMaybeLater: () -> Unit,
) {
    val font = MaterialTheme.typography.titleMedium.fontFamily
    Surface(modifier = Modifier.fillMaxSize(), color = ScreenBg) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.rateus_phone),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(5) {
                    Icon(Icons.Rounded.Star, null, tint = Gold, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Enjoying", fontFamily = font, fontWeight = FontWeight.W700, fontSize = 28.sp, color = Ink)
            Text("Messages app?", fontFamily = font, fontWeight = FontWeight.W700, fontSize = 28.sp, color = Brand)
            Spacer(Modifier.height(14.dp))
            Text(
                "Your feedback helps us improve and builds a better experience",
                fontFamily = font, fontWeight = FontWeight.W500, fontSize = 15.sp,
                color = Sub, textAlign = TextAlign.Center, lineHeight = 21.sp,
                modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 24.dp),
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onRateNow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
            ) {
                Text("Rate Us Now", fontFamily = font, fontWeight = FontWeight.W700, fontSize = 18.sp)
            }
            TextButton(onClick = onMaybeLater, modifier = Modifier.padding(top = 2.dp)) {
                Text("Maybe later", fontFamily = font, fontWeight = FontWeight.W500, color = Sub)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
