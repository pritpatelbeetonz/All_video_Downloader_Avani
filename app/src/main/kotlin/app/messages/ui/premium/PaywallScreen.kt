package app.messages.ui.premium

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.R
import app.messages.monetization.iap.PlusProduct

private val Brand = Color(0xFF0057CC)
private val BrandGradTop = Color(0xFF2C77E8)
private val BrandGradBot = Color(0xFF0043A8)
private val Ink = Color(0xFF000000)
private val InkSub = Color(0x99000000) // rgba(0,0,0,0.6)
private val Ink70 = Color(0xB3000000) // rgba(0,0,0,0.7)
private val PriceInk = Color(0xFF111827)
private val PeriodInk = Color(0xFF6B7280)
private val White70 = Color(0xB3FFFFFF)
private val Badge = Color(0xFFFFD93F)
private val CardBorder = Color(0xFFE5E7EB)

private data class Perk(val text: String)

// Two-column benefit grid, exactly as the Figma "premium" frame (43:1407).
// Each label is broken to a tidy two lines so the grid reads evenly.
private val PerksLeft = listOf("No ads,\nanywhere", "Auto Google\nDrive backup", "All themes &\nbubble styles")
private val PerksRight = listOf("Unlimited\nscheduling", "Per-chat lock &\nprivate folder", "Export\nto PDF")

/**
 * "Upgrade to Plus" paywall — a faithful build of the Figma `premium` frame
 * (43:1407): aurora background, brand bubble, a two-column verified-check benefit
 * grid, then Yearly (blue, "SAVE" badge) / Weekly / Lifetime plan cards and a pill
 * Subscribe button. Single screen, no scroll. RevenueCat wiring + real INR prices
 * preserved; the selected plan is the highlighted blue card.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    vm: PaywallViewModel = hiltViewModel(),
) {
    val font = MaterialTheme.typography.titleMedium.fontFamily
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val purchasing by vm.purchasing.collectAsStateWithLifecycle()
    val isPremium by vm.isPremium.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Color(0xFFF6F6F6))) {
        Image(
            painter = painterResource(R.drawable.paywall_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar: close (X) + Restore
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0x0F000000))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "Close", tint = Ink, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Restore",
                    fontFamily = font, fontWeight = FontWeight.W500, fontSize = 14.sp, color = Ink70,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { vm.restore() }.padding(6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.width(56.dp).height(51.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text("Upgrade to Plus", fontFamily = font, fontWeight = FontWeight.W700, fontSize = 25.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "Get enhanced messaging tools with extra privacy benefits.",
                fontFamily = font, fontWeight = FontWeight.W500, fontSize = 15.sp,
                color = InkSub, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp),
            )

            if (isPremium) {
                Spacer(Modifier.height(34.dp))
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(Brand),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.height(18.dp))
                Text("You're on Messages Plus", fontFamily = font, fontWeight = FontWeight.W700, fontSize = 20.sp, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text("Thanks for your support.", fontFamily = font, fontWeight = FontWeight.W500, fontSize = 14.sp, color = InkSub)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
                ) { Text("Done", fontFamily = font, fontWeight = FontWeight.W700, fontSize = 18.sp) }
                Spacer(Modifier.height(20.dp))
                return@Column
            }

            // Two-column benefit grid (directly on the aurora, per Figma)
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PerksLeft.forEach { Benefit(it, font) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PerksRight.forEach { Benefit(it, font) }
                }
            }

            Spacer(Modifier.weight(1f))

            // Plan cards
            vm.products.forEach { product ->
                PlanCard(product, selected = product.id == selectedId, onSelect = { vm.select(product.id) }, font = font)
                Spacer(Modifier.height(7.dp))
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.purchase(onComplete = onClose) },
                enabled = !purchasing,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (vm.selectedProduct.period == "one-time") "Buy lifetime" else "Subscribe",
                            fontFamily = font, fontWeight = FontWeight.W700, fontSize = 18.sp,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward, null,
                        modifier = Modifier.align(Alignment.CenterEnd).size(24.dp),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                "By continuing, you agree to our Terms of Use & Privacy Policy.\nSubscriptions auto renew, cancel anytime.",
                fontFamily = font, fontWeight = FontWeight.W500, fontSize = 12.sp,
                color = Ink70, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun Benefit(text: String, font: FontFamily?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.Verified, null, tint = Brand, modifier = Modifier.size(24.dp))
        Text(text, fontFamily = font, fontWeight = FontWeight.W500, fontSize = 13.sp, lineHeight = 16.sp, color = Ink, maxLines = 2)
    }
}

@Composable
private fun PlanCard(
    product: PlusProduct,
    selected: Boolean,
    onSelect: () -> Unit,
    font: FontFamily?,
) {
    val shape = RoundedCornerShape(16.dp)
    val best = product.isBestValue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(Brush.horizontalGradient(listOf(BrandGradTop, BrandGradBot)))
                else Modifier.background(Color.White),
            )
            .then(if (selected) Modifier else Modifier.border(1.dp, CardBorder, shape))
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (best) {
            Text(product.title, fontFamily = font, fontWeight = FontWeight.W700, fontSize = 20.sp, color = if (selected) Color.White else Ink)
            Spacer(Modifier.width(9.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Badge).padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text("SAVE 75%", fontFamily = font, fontWeight = FontWeight.W800, fontSize = 11.sp, color = Ink)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(product.price, fontFamily = font, fontWeight = FontWeight.W700, fontSize = 20.sp, color = if (selected) Color.White else PriceInk)
                Text(product.period, fontFamily = font, fontWeight = FontWeight.W600, fontSize = 13.sp, color = if (selected) White70 else PeriodInk)
            }
        } else {
            Column {
                Text(product.title, fontFamily = font, fontWeight = FontWeight.W700, fontSize = 20.sp, color = if (selected) Color.White else Ink)
                Text(product.period, fontFamily = font, fontWeight = FontWeight.W500, fontSize = 14.sp, color = if (selected) White70 else PeriodInk)
            }
            Spacer(Modifier.weight(1f))
            Text(product.price, fontFamily = font, fontWeight = FontWeight.W700, fontSize = 20.sp, color = if (selected) Color.White else PriceInk)
        }
    }
}
