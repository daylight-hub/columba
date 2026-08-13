package network.columba.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A glossy "shiny red" call-to-action button in LCS styling.
 *
 * Used sparingly for a few high-emphasis actions (e.g. "Buy RNode Radios").
 * Rendered as a Box so we get a vertical red gradient + a top highlight for the
 * glossy look, independent of the active Material color scheme.
 */
@Composable
fun ShinyRedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier =
            modifier
                .shadow(elevation = 8.dp, shape = shape)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(LcsRedLight, LcsRed, LcsRedDeep),
                    ),
                )
                .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // Top gloss highlight — a soft white sheen over the upper portion.
        //
        // matchParentSize(), NOT fillMaxWidth(): a fillMaxWidth child expands to
        // the incoming max constraint and drags the whole button out to the full
        // available width, so the pill stretched edge to edge and the label sat
        // wherever the parent's alignment left it. matchParentSize() measures
        // against the resolved parent instead, so the button now wraps its
        // label — which lets the enclosing Column centre the button itself, and
        // contentAlignment centre the label within it.
        //
        // Because this is now full height rather than a fixed 20.dp, the sheen
        // is held to the top by gradient stops instead of by the box bounds.
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.35f),
                            0.45f to Color.Transparent,
                            1.0f to Color.Transparent,
                        ),
                    ),
        )
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
