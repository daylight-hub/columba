package network.columba.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .alpha(alpha),
            contentAlignment = Alignment.Center,
        ) {
            // The logo is centred on its own, NOT as the first item of a column
            // containing the text. That is what stops this reading as a second
            // splash screen: the system splash centres its icon, so if the logo
            // here were pushed upward to make room for the wordmark below it,
            // the logo would visibly jump at the handover and the user would
            // see two screens instead of one.
            //
            // Centring the logo alone keeps it exactly where the system left
            // it. The wordmark is offset beneath it, so all the user perceives
            // is the words appearing under a logo that never moved.
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(LOGO_SIZE),
            )

            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .offset(y = LOGO_SIZE / 2 + 8.dp)
                        .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Stacked, not side by side: together these need roughly 300 dp
                // on one line, which clips on the 320 dp-wide screens still in
                // use on the Android versions this has to cover.
                Text(
                    text = "Liberty Chat",
                    color = LibertyNavy40,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "powered by Columba",
                    color = LibertySilver40,
                    fontSize = 14.sp,
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
 * Logo size, matched to the icon the system splash draws.
 *
 * Fixed rather than scaled to the viewport: the whole point is that the logo
 * lands exactly where the system splash left it, and a viewport-relative size
 * would land differently on every device. 192 dp is the inner icon size the
 * platform splash uses for a non-adaptive drawable, and androidx
 * core-splashscreen matches it on older releases.
 */
private val LOGO_SIZE = 192.dp
