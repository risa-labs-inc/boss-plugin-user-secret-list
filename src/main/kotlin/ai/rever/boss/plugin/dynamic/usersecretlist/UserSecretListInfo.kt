package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Key

/**
 * Panel info for the retired My Secrets panel.
 *
 * The id, the icon and the slot are all unchanged on purpose. A saved sidebar layout keys on
 * the panel id, and the user has had this Key icon in that slot since their first run - moving
 * or renaming it would hide the one notice that explains where their list went. It now renders
 * [MovedNotice] and reads no secrets at all.
 */
object UserSecretListInfo : PanelInfo {
    override val id = PanelId("user-secret-list", 25)
    override val displayName = "My Secrets"
    override val icon = FeatherIcons.Key
    override val defaultSlotPosition = right.top.bottom
}
