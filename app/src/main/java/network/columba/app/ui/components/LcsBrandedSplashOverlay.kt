package network.columba.app.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import network.columba.app.R
import network.columba.app.ui.theme.LibertyNavy40
import network.columba.app.ui.theme.LibertySilver40

/**
 * LCS: branded splash overlay for devices the platform branding slot cannot reach.
 *
 * ## Why this exists
 *
 * The "Liberty Chat — powered by Columba" wordmark is normally drawn by the
 * system via `android:windowSplashScreenBrandingImage` (see
 * `values-v31/themes.xml`). That attribute is **API 31+ and platform-only** —
 * androidx `core-splashscreen` deliberately does not emulate the branding slot
 * on older releases, so there is no theme-level way to get the wordmark onto
 * Android 10 or 11. The system splash on those devices shows the launcher icon
 * on white and nothing else.
 *
 * So on API < 31 the app draws both marks itself, immediately after the compat
 * splash hands over: the launcher logo, and beneath it the wordmark as live
 * text rather than the `splash_branding` PNG, so it stays crisp at any density
 * and matches the Liberty palette exactly.
 *
 * On API 31+ this renders nothing — the system already drew both, and drawing
 * them again would flash.
 *
 * ## Layout
 *
 * Both marks must survive the small screens that ship with the Android versions
 * this targets (360x640 dp is common, and 320 dp wide still exists). So:
 *
 * - The logo is sized as a fraction of the viewport rather than a fixed dp, and
 *   capped, so it cannot crowd out the text on a short screen.
 * - The wordmark is stacked, not laid out in a row. Side by side, "Liberty Chat"
 *   plus "powered by Columba" needs roughly 300 dp and would clip at 320.
 * - Text is centred and given horizontal padding so descenders and the longer
 *   attribution line never touch the edges.
 */
@Composable
fun LcsBrandedSplashOverlay(visibleDurationMs: Long = 900L) {
    // API 31+ gets both marks from the platform splash. Nothing to do.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return

    var visible by remember { mutableStateOf(true) }
    if (!visible) return

    // Fade rather than cut, so the handover to the app does not blink.
    var fading by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "lcs_splash_fade",
    )

    LaunchedEffect(Unit) {
        delay(visibleDurationMs)
        fading = true
        delay(240)
        visible = false
    }

    Dialog(
        onDismissRequest = { },
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                // Without this the dialog is inset to the platform's default
                // dialog width and the white field would not cover the screen.
                usePlatformDefaultWidth = false,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .alpha(alpha),
            contentAlignment = Alignment.Center,
        ) {
            // Scale the logo to the viewport, but never let it exceed 180 dp or
            // fall below 96 dp — the first keeps it from dominating a tablet,
            // the second keeps it recognisable on a small phone.
            val logoSize = (minOf(maxWidth, maxHeight) * 0.34f).coerceIn(96.dp, 180.dp)

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(logoSize),
                )

                Spacer(Modifier.height(12.dp))

                // Wordmark, stacked. Mirrors splash_branding.png: navy product
                // name over silver attribution.
                Text(
                    text = "Liberty Chat",
                    color = LibertyNavy40,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "powered by Columba",
                    color = LibertySilver40,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
