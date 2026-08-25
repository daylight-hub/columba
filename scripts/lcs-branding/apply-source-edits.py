#!/usr/bin/env python3
"""Apply the Liberty Chat (LCS) rebrand edits to a checked-out columba tree.
Each edit asserts it changed exactly what was expected so a drifting upstream
fails loudly instead of silently no-op'ing."""
import re, sys, pathlib

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")

def edit(rel, old, new, count=1):
    p = ROOT / rel
    s = p.read_text()
    n = s.count(old)
    assert n == count, f"[{rel}] expected {count} match(es) for anchor, found {n}:\n---\n{old[:200]}\n---"
    p.write_text(s.replace(old, new))
    print(f"OK  {rel}  ({count}x)")

def remove_balanced_call(rel, call_start):
    """Remove a Kotlin call like Foo( ... ) starting at the first occurrence of
    call_start (which must include the opening paren), through its matching )."""
    p = ROOT / rel
    s = p.read_text()
    i = s.index(call_start)
    depth = 0
    j = i + len(call_start) - 1  # index of the opening paren
    assert s[j] == "("
    k = j
    while k < len(s):
        if s[k] == "(":
            depth += 1
        elif s[k] == ")":
            depth -= 1
            if depth == 0:
                break
        k += 1
    # swallow leading indentation on the call's line and a trailing newline
    line_start = s.rfind("\n", 0, i) + 1
    end = k + 1
    if s[end:end+1] == "\n":
        end += 1
    p.write_text(s[:line_start] + s[end:])
    print(f"OK  {rel}  (removed {call_start.rstrip('(')} call)")

# ---- Item 1: app name (launcher label) ------------------------------------
edit("app/build.gradle.kts",
     'resValue("string", "app_name", "Columba (Kotlin)")',
     'resValue("string", "app_name", "Liberty Chat (Kotlin)")')
edit("app/build.gradle.kts",
     'resValue("string", "app_name", "Columba")',
     'resValue("string", "app_name", "Liberty Chat")')
edit("app/src/main/res/values/strings.xml",
     "<string name=\"offline_banner_shutdown\">Columba is offline</string>",
     "<string name=\"offline_banner_shutdown\">Liberty Chat is offline</string>")

# ---- Item 4: default RNode frequency = US slot 51 (914.875 MHz) ------------
edit("app/src/main/java/network/libertychat/app/data/model/RNodeRegionalPreset.kt",
     '            "us_915" -> minOf(50, numSlots - 1)\n',
     '            "us_915" -> minOf(51, numSlots - 1) // LCS default: slot 51 = 914.875 MHz\n')

# ---- Item 6: "Long Fast" badge -> "LCS Recommended" ------------------------
edit("app/src/main/java/network/libertychat/app/ui/screens/rnode/ModemPresetStep.kt",
     'text = "Recommended",',
     'text = "LCS Recommended",')

# ---- Item 3: announce icon left of the search icon ------------------------
# 3a. give SearchableTopAppBar a leadingActions slot rendered before search
edit("app/src/main/java/network/libertychat/app/ui/components/SearchableTopAppBar.kt",
     "    searchPlaceholder: String = \"Search...\",\n    additionalActions: @Composable (RowScope.() -> Unit)? = null,\n) {",
     "    searchPlaceholder: String = \"Search...\",\n    leadingActions: @Composable (RowScope.() -> Unit)? = null,\n    additionalActions: @Composable (RowScope.() -> Unit)? = null,\n) {")
edit("app/src/main/java/network/libertychat/app/ui/components/SearchableTopAppBar.kt",
     "            actions = {\n                IconButton(onClick = onSearchToggle) {",
     "            actions = {\n                leadingActions?.invoke(this)\n                IconButton(onClick = onSearchToggle) {")
# 3b. Chats screen: add the announce IconButton + import
edit("app/src/main/java/network/libertychat/app/ui/screens/ChatsScreen.kt",
     "import androidx.compose.material.icons.filled.Chat\n",
     "import androidx.compose.material.icons.filled.Campaign\nimport androidx.compose.material.icons.filled.Chat\n")
edit("app/src/main/java/network/libertychat/app/ui/screens/ChatsScreen.kt",
     '                searchPlaceholder = "Search conversations...",\n                additionalActions = {',
     '                searchPlaceholder = "Search conversations...",\n'
     '                leadingActions = {\n'
     '                    // LCS: manual announce trigger, left of the search icon\n'
     '                    IconButton(onClick = {\n'
     '                        settingsViewModel.triggerManualAnnounce()\n'
     '                        Toast.makeText(context, "Announcing\u2026", Toast.LENGTH_SHORT).show()\n'
     '                    }) {\n'
     '                        Icon(\n'
     '                            imageVector = Icons.Default.Campaign,\n'
     '                            contentDescription = "Announce",\n'
     '                        )\n'
     '                    }\n'
     '                },\n'
     '                additionalActions = {')

# ---- Item 5: TX-power radio note ------------------------------------------
edit("app/src/main/java/network/libertychat/app/ui/screens/rnode/ReviewConfigStep.kt",
     '                        placeholder = { Text("0-$maxTxPower") },\n                    )\n                }\n\n                // Airtime limits, interface mode, and framebuffer are not relevant for transport mode',
     '                        placeholder = { Text("0-$maxTxPower") },\n                    )\n                }\n\n'
     '                // LCS: TX-power ceilings by RNode hardware\n'
     '                Text(\n'
     '                    "Heltec V4 radio \u2014 max 28 dBm \u00b7 RAK or LILYGO \u2014 max 22 dBm",\n'
     '                    style = MaterialTheme.typography.bodySmall,\n'
     '                    color = MaterialTheme.colorScheme.onSurfaceVariant,\n'
     '                )\n\n'
     '                // Airtime limits, interface mode, and framebuffer are not relevant for transport mode')

# ---- Item 7: remove "Display Logo on RNode" toggle ------------------------
edit("app/src/main/java/network/libertychat/app/ui/screens/rnode/ReviewConfigStep.kt",
     '''                    Spacer(Modifier.height(16.dp))

                    // Display logo on RNode toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Display Logo on RNode",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Show Columba logo on RNode's display when connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = state.enableFramebuffer,
                            onCheckedChange = { viewModel.updateEnableFramebuffer(it) },
                        )
                    }
''',
     '')

# ---- Item 8: TCP client servers -> single LCS node ------------------------
p = ROOT / "app/src/main/java/network/libertychat/app/data/model/TcpCommunityServer.kt"
s = p.read_text()
start = s.index("        listOf(")
end = s.index("        )", start) + len("        )")
new_list = ('        listOf(\n'
            '            // LCS: single official Liberty Communication Systems public node\n'
            '            TcpCommunityServer("LCS Public Node", "public.lcs.network", 4245, isBootstrap = true),\n'
            '        )')
assert "Beleth RNS Hub" in s[start:end]
p.write_text(s[:start] + new_list + s[end:])
print("OK  TcpCommunityServer.kt  (server list replaced)")

# ---- Item 9: Share APK rebrand + LCS filename -----------------------------
edit("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/ShareColumbaCard.kt",
     'title = "Share Columba",', 'title = "Share Liberty Chat",')
edit("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/ShareColumbaCard.kt",
     '"Share the Columba app with someone nearby. "',
     '"Share the Liberty Chat app with someone nearby. "')
edit("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/ShareColumbaCard.kt",
     'Text("Share Columba APK")', 'Text("Share Liberty Chat APK")')
edit("app/src/main/java/network/libertychat/app/viewmodel/ApkSharingViewModel.kt",
     '"columba-${network.libertychat.app.BuildConfig.VERSION_NAME}.apk"',
     '"liberty-chat-${network.libertychat.app.BuildConfig.VERSION_NAME}.apk"')

# ---- Item 10: remove built-in RNode flasher entry (Settings card) ---------
edit("app/src/main/java/network/libertychat/app/ui/screens/SettingsScreen.kt",
     "import network.libertychat.app.ui.screens.settings.cards.RNodeFlasherCard\n", "")
remove_balanced_call("app/src/main/java/network/libertychat/app/ui/screens/SettingsScreen.kt",
                     "RNodeFlasherCard(")

# ---- Item 11: update check -> LCS repo ------------------------------------
edit("app/src/main/java/network/libertychat/app/service/UpdateChecker.kt",
     'private const val OWNER = "torlando-tech"',
     'private const val OWNER = "daylight-hub"')

# ---- Item 12: remove Report Bug button ------------------------------------
edit("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/AboutCard.kt",
     "import androidx.compose.material.icons.filled.BugReport\n", "")
edit("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/AboutCard.kt",
     '''
            // Report Bug Button
            OutlinedButton(
                onClick = onReportBug,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Report Bug")
            }
''', "\n")

# ---- Item 14: remove GitHub / Report Issue / About Reticulum links --------
edit("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/AboutCard.kt",
     '''            // Links
            InfoSection(title = "Links & Resources") {
                LinkButton("GitHub Repository", "https://github.com/torlando-tech/columba", context)
                LinkButton("Report an Issue", "https://github.com/torlando-tech/columba/issues", context)
                LinkButton("About Reticulum", "https://reticulum.network/", context)
            }

            HorizontalDivider()

''', "")
# LinkButton helper is now unused -> remove it to keep detekt happy
remove_balanced_call("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/AboutCard.kt",
                     "private fun LinkButton(")
# clean the now-dangling body of the old LinkButton (its {...} block)
p = ROOT / "app/src/main/java/network/libertychat/app/ui/screens/settings/cards/AboutCard.kt"
s = p.read_text()
# after removing the fun signature+params, an orphan block may remain; strip a leading
# "{ TextButton(...) }" that followed the signature.
orphan = '''{
    TextButton(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}
'''
if orphan in s:
    s = s.replace(orphan, "")
    p.write_text(s)
    print("OK  AboutCard.kt  (orphan LinkButton body removed)")

# ---- Item 13: About branding + license credit ----------------------------
edit("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/AboutCard.kt",
     'contentDescription = "Columba Logo",', 'contentDescription = "Liberty Chat Logo",')
edit("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/AboutCard.kt",
     '            Text(\n                text = "Columba",\n                style = MaterialTheme.typography.headlineSmall,',
     '            Text(\n                text = "Liberty Chat",\n                style = MaterialTheme.typography.headlineSmall,')
edit("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/AboutCard.kt",
     'text = "Native Android messaging app using Bluetooth LE, TCP, or RNode (LoRa) over LXMF and Reticulum",',
     'text = "Liberty Chat \u2014 messaging over Bluetooth LE, TCP, or RNode (LoRa) using LXMF and Reticulum",')
edit("app/src/main/java/network/libertychat/app/ui/screens/settings/cards/AboutCard.kt",
     '''                Text(
                    text = "© 2025–${network.libertychat.app.BuildConfig.COPYRIGHT_YEAR} Columba Contributors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/torlando-tech/columba/blob/main/LICENSE.md".toUri())
                        context.startActivity(intent)
                    },
                ) {
                    Text("View License", style = MaterialTheme.typography.bodySmall)
                }''',
     '''                Text(
                    text = "Liberty Chat — a Liberty Communication Systems, Inc. distribution",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Based on Columba © 2025–${network.libertychat.app.BuildConfig.COPYRIGHT_YEAR} " +
                        "Columba Contributors (torlando-tech) — original design & code",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "LCS branding & modifications © ${network.libertychat.app.BuildConfig.COPYRIGHT_YEAR} " +
                        "Liberty Communication Systems, Inc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/daylight-hub/columba/blob/liberty-chat/LICENSE.md".toUri())
                        context.startActivity(intent)
                    },
                ) {
                    Text("View License", style = MaterialTheme.typography.bodySmall)
                }''')

print("\nAll source edits applied.")
