package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VpnKey

object UserSecretListInfo : PanelInfo {
    override val id = PanelId("user-secret-list", 25)
    override val displayName = "My Secrets"
    override val icon = Icons.Outlined.VpnKey
    override val defaultSlotPosition = right.top.bottom
}
