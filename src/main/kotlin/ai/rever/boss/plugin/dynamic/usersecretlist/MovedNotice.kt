package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * All this plugin does now: say where its list went.
 *
 * A panel rather than nothing at all. Registering no panel would make a sidebar icon the user
 * has had since their first run silently stop working, with nowhere to find out why - the
 * failure mode of a retirement is a user who thinks their secrets are gone.
 */
@Composable
internal fun MovedNotice(link: SecretManagerLink?) {
    // Re-read on every entry into composition rather than once at registration: the user can
    // install Secret Manager from the Toolbox and come straight back here, and a value captured
    // when the plugin loaded would still say "not installed" and offer the Toolbox again.
    var installed by remember { mutableStateOf(link?.secretManagerInstalled() == true) }
    val canOpenPanels = remember(link) { link?.canOpenPanels() == true }
    val action = movedNoticeAction(secretManagerInstalled = installed, canOpenPanels = canOpenPanels)
    val scope = rememberCoroutineScope()

    // Cheap: isPluginLoaded is a map lookup, no I/O. It is what makes the button flip while the
    // notice is on screen, which is the whole path this feature exists for - install it, come
    // back, and the same button now opens it.
    LaunchedEffect(link) {
        while (link != null) {
            delay(INSTALL_POLL_MS)
            installed = link.secretManagerInstalled()
        }
    }

    BossTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(BossThemeColors.BackgroundColor)
                    // Scrollable because this panel lives in a slot the user can drag short,
                    // and five stacked elements in a fixed Box put the last line out of reach.
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    // The Key the user just clicked in the sidebar, not a Share glyph: the whole
                    // reason the panel id and icon are unchanged is "this is the thing you
                    // clicked", and the hero icon is where that lands hardest.
                    Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = BossThemeColors.TextSecondary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    "My Secrets has moved",
                    color = BossThemeColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "This list is now the \"Shared with me\" section of Secret Manager, " +
                        "alongside the secrets you own.",
                    color = BossThemeColors.TextSecondary,
                    fontSize = 13.sp,
                    // Centred like the column that holds it: without this the block is centred
                    // but every wrapped line inside it is left-ragged.
                    textAlign = TextAlign.Center,
                )
                when (action) {
                    MovedNoticeAction.OPEN_SECRET_MANAGER ->
                        NoticeButton(
                            label = "Open Secret Manager",
                            icon = Icons.Default.Lock,
                            onClick = { scope.launch { link?.openSecretManager() } },
                        )

                    MovedNoticeAction.INSTALL_FROM_TOOLBOX ->
                        NoticeButton(
                            // Says what it does. No plugin-facing api installs another plugin, so
                            // this opens the Toolbox rather than implying a one-press install.
                            label = "Install Secret Manager",
                            icon = Icons.Default.Lock,
                            supporting = "Opens the Toolbox, where you can install it.",
                            onClick = { scope.launch { link?.openToolbox() } },
                        )

                    // An older host, where openPanel does not exist: a button would be a control
                    // that cannot work, so say where to look instead. Both states, because the
                    // plugin cannot tell which one it is in from here.
                    MovedNoticeAction.DESCRIBE_ONLY ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(BossThemeColors.SurfaceColor, RoundedCornerShape(6.dp))
                                    .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = BossThemeColors.AccentColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "Open Secret Manager from the sidebar, or install it from the " +
                                    "Plugin Store.",
                                color = BossThemeColors.TextPrimary,
                                fontSize = 13.sp,
                            )
                        }
                }
                Text(
                    "This panel can be uninstalled. A recent BOSS removes it for you once " +
                        "Secret Manager is installed.",
                    color = BossThemeColors.TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** How often the notice re-checks whether Secret Manager has appeared. A map lookup, no I/O. */
private const val INSTALL_POLL_MS = 1_500L

@Composable
private fun NoticeButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    supporting: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    backgroundColor = BossThemeColors.AccentColor,
                    contentColor = BossThemeColors.TextPrimary,
                ),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        if (supporting != null) {
            Text(
                supporting,
                color = BossThemeColors.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
