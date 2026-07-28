package network.columba.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import network.columba.app.R
import network.columba.app.ui.theme.LibertyNavy40
import network.columba.app.ui.theme.LibertySilver40

/**
 * LCS: the "Liberty Chat — powered by Columba" splash wordmark.
 *
 * ## Why the app draws this instead of the system
 *
 * Android's own branding slot, `android:windowSplashScreenBrandingImage`, is
 * API 31+ and platform-only — androidx `core-splashscreen` deliberately does not
 * emulate it, so there is no theme-level way to put the wordmark on Android 10
 * or 11. Relying on it would mean the splash said one thing on a new phone and
 * another on an old one.
 *
 * Embedding the wordmark in the splash *icon* is not a workaround either: on
 * API 31+ the platform masks that icon to a circle and clips the outer third, so
 * a wide logo-plus-text image comes out mangled on exactly the devices where the
 * branding slot would have worked.
 *
 * So the app draws it, on every API level, and the platform branding attribute
 * has been removed from the v31/v33 themes. Every device now shows the same
 * thing: system splash with the logo on white, then this — logo above the
 * wordmark — then the app.
 *
 * ## How it is hosted
 *
 * Declared at the theme root in `MainActivity`, outside the app's `Surface`, so
 * it covers the whole UI while drawing in the same window. Deliberately not a
 * `Dialog`: that would be a separate window, and the frame or two the system
 * takes to attach one is long enough for the app to show through.
 *
 * ## Why [show] exists
 *
 * `setContent` composes the entire tree during `onCreate`, while the system
 * splash is still covering the window. A dwell timer started on composition
 * therefore runs and expires *behind* the splash — which is what happened in
 * 1.2.0, where the wordmark was drawn and removed before the splash ever
 * lifted, leaving users with a logo and no words.
 *
 * [show] is flipped from `SplashScreen.setOnExitAnimationListener` in
 * `MainActivity`, so the dwell starts when the screen is genuinely visible.
 *
 * @param show true once the system splash has left the window.
 * @param visibleDurationMs how long the wordmark stays up before fading.
 */
@Composable
fun LcsBrandedSplashOverlay(
    show: Boolean,
    visibleDurationMs: Long = 1100L,
) {
    // Latches on the first `show`, so a recomposition cannot replay the splash
    // mid-session. rememberSaveable rather than remember: the flag driving
    // `show` is process-scoped, so on an Activity recreation (rotation, "don't
    // keep activities") it is already true — a plain remember would reset here
    // and replay the splash on every rotation.
    var hasRun by rememberSaveable { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var fading by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (!show || hasRun) return@LaunchedEffect
        hasRun = true
        visible = true
        delay(visibleDurationMs)
        fading = true
        delay(FADE_MS.toLong())
        visible = false
    }

    if (!visible) return

    // Fades out only. There is deliberately no fade IN: the overlay has to be
    // fully painted the instant the system splash is removed, or the user sees
    // a flash of the app underneath and the two screens stop reading as one.
    val alpha by animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = tween(durationMillis = FADE_MS),
        label = "lcs_splash_fade",
    )

    // No Dialog: this draws in the app's own window, so it is on screen the
    // same frame the system splash is removed. A Dialog would need its own
    // window attached first, and the frame or two that takes is enough for the
    // app UI to show through underneath.
    //
    // pointerInput swallows taps so nothing behind can be pressed while the
    // splash is up.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .alpha(alpha),
            contentAlignment = Alignment.Center,
        ) {
            // Logo and wordmark are stacked as ordinary siblings in one centred
            // Column, with a real gap between them. No overlap tricks: the
            // previous version centred the logo and then offset the text up over
            // it, which only worked for an adaptive-icon foreground with a
            // transparent safe zone. The LCS logo fills its whole image, so that
            // offset pulled the words up into the logo. A Column that just lays
            // them out top-to-bottom needs no assumptions about the artwork.
            val logoSize = minOf(LOGO_SIZE, maxWidth * 0.7f, maxHeight * 0.4f)

            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // The only logo drawn at launch — the system splash uses a blank
                // icon (splash_icon_blank.xml) so this is the single source.
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(logoSize),
                )

                Spacer(Modifier.height(24.dp))

                // Stacked, not side by side: together on one line these need
                // roughly 300 dp, which clips on 320 dp-wide screens.
                Text(
                    text = "Liberty Chat",
                    color = LibertyNavy40,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "powered by Columba",
                    color = LibertySilver40,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val FADE_MS = 260

/**
 * Drawn size of the logo, clamped against the viewport at the call site.
 *
 * The LCS artwork fills its whole image (no transparent safe zone), so this is
 * the actual visible size of the mark.
 */
private val LOGO_SIZE = 200.dp
