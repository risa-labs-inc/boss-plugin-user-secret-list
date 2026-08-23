package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * All this plugin does now: say where its list went.
 *
 * A panel rather than nothing at all. Registering no panel would make a sidebar icon the user
 * has had since their first run silently stop working, with nowhere to find out why - the
 * failure mode of a retirement is a user who thinks their secrets are gone.
 */
@Composable
internal fun MovedNotice() {
    BossTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(BossThemeColors.BackgroundColor)
                    .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Share,
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
                )
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
                        "Open Secret Manager from the sidebar.",
                        color = BossThemeColors.TextPrimary,
                        fontSize = 13.sp,
                    )
                }
                Text(
                    "This panel can be uninstalled. A recent BOSS removes it for you once " +
                        "Secret Manager is installed.",
                    color = BossThemeColors.TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
