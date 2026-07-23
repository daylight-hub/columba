package network.columba.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import network.columba.app.ui.model.CodecProfile

/**
 * LCS: call-quality advisory shown at the top of the call screen.
 *
 * ## What it is for
 *
 * A LoRa link is often far too slow for the codec a call opened with, and the
 * failure mode is unhelpful: the audio simply breaks up, with nothing on screen
 * explaining why or what to do. This surfaces the link measurement the app
 * already takes before dialling, names the codec that would fit, and lets the
 * user switch to it in one tap.
 *
 * ## Why it is advisory rather than automatic
 *
 * The app does not downgrade the codec on its own. LXST exposes no mid-call
 * quality telemetry — no jitter, packet-loss or decoder-underrun counters — so
 * the only way to measure a live link is an active probe, which sends traffic
 * over the very link that is already struggling. On a Long Fast RNode link at
 * roughly 1 kbps, probing during a call would cause the degradation it was
 * meant to detect. So the measurement is taken once, before the call, and the
 * decision is left with the user.
 *
 * Switching is real and immediate: LXST signals the peer, whose own LXST
 * follows, so one tap moves both ends. That is upstream LXST protocol, so it
 * works against Sideband and MeshChat too.
 *
 * The switch is reversible. The dropdown offers Opus alongside Codec2, so a
 * call that was downgraded — or that has since moved onto a faster interface —
 * can go back up without hanging up and redialling.
 *
 * @param currentProfile codec the call is running on now.
 * @param recommendedProfile codec that fits the measured link, or null if the
 *   link was never measured or is comfortably fast enough.
 * @param measuredBps measured link rate in bits per second, or null if unknown.
 * @param isPttMode whether half-duplex is already engaged.
 * @param onSelectProfile switch the call to this codec.
 * @param onEnablePtt engage half-duplex.
 * @param onDismiss hide the advisory for the rest of this call.
 */
@Composable
fun CallQualityAdvisory(
    currentProfile: CodecProfile,
    recommendedProfile: CodecProfile?,
    measuredBps: Int?,
    isPttMode: Boolean,
    onSelectProfile: (CodecProfile) -> Unit,
    onEnablePtt: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val show = recommendedProfile != null && recommendedProfile != currentProfile

    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        if (recommendedProfile == null) return@AnimatedVisibility

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Slow link detected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text =
                        buildString {
                            if (measuredBps != null) {
                                append("This link measures about ")
                                append(formatBitrate(measuredBps))
                                append(". ")
                            }
                            append(currentProfile.displayName)
                            append(" needs more than that — ")
                            append(recommendedProfile.displayName)
                            append(" will sound clearer here.")
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CodecMenuButton(
                        currentProfile = currentProfile,
                        recommendedProfile = recommendedProfile,
                        onSelectProfile = onSelectProfile,
                    )

                    if (!isPttMode) {
                        TextButton(onClick = onEnablePtt) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Use push-to-talk", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                if (!isPttMode) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text =
                            "Push-to-talk sends audio one way at a time, which roughly halves " +
                                "what the link has to carry.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/**
 * Codec picker for an in-progress call.
 *
 * Lists both families under headers — Codec2 for slow links, Opus for fast
 * ones — so the switch works in both directions. Grouping matters here: the
 * display names ("Low Bandwidth", "Medium Quality") do not make the underlying
 * codec obvious, and on a struggling link the distinction is the whole point.
 *
 * The experimental low-latency profiles are omitted; mid-call is the wrong
 * moment to try one.
 */
@Composable
private fun CodecMenuButton(
    currentProfile: CodecProfile,
    recommendedProfile: CodecProfile,
    onSelectProfile: (CodecProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Change codec", style = MaterialTheme.typography.labelLarge)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            CodecMenuSection("Codec2 — for LoRa links")
            CodecProfile.codec2Profiles().forEach { profile ->
                CodecMenuRow(profile, currentProfile, recommendedProfile) {
                    expanded = false
                    onSelectProfile(profile)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            CodecMenuSection("Opus — for WiFi or TCP")
            CodecProfile.opusProfiles().forEach { profile ->
                CodecMenuRow(profile, currentProfile, recommendedProfile) {
                    expanded = false
                    onSelectProfile(profile)
                }
            }
        }
    }
}

@Composable
private fun CodecMenuSection(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun CodecMenuRow(
    profile: CodecProfile,
    currentProfile: CodecProfile,
    recommendedProfile: CodecProfile,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = {
            if (profile == currentProfile) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Currently in use",
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (profile == recommendedProfile) {
                        Spacer(Modifier.width(8.dp))
                        RecommendedTag()
                    }
                }
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun RecommendedTag() {
    Box(
        modifier =
            Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp),
                ).padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = "Recommended",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/** Render a raw bits-per-second figure the way a person would say it. */
private fun formatBitrate(bps: Int): String =
    when {
        bps >= 1_000_000 -> "${bps / 1_000_000} Mbps"
        bps >= 1_000 -> "${bps / 1_000} kbps"
        else -> "$bps bps"
    }
