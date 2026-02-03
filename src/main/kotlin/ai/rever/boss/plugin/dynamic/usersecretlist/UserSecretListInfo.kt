package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Key

/**
 * Panel info for User Secret List (Read-Only)
 *
 * This panel allows authenticated users to view secrets they own or that have been shared with them.
 * Features:
 * - Read-only view (website:username only, password masked)
 * - Ownership badges (Owner vs Shared)
 * - Client-side search/filter
 * - Copy website and username to clipboard
 * - View metadata (tags, notes, expiration, shared by info)
 * - No edit/delete/share actions
 *
 * Access Control:
 * - Accessible to all authenticated users
 * - Permission check for 'secrets.read' pending RBAC permission system (see docs/RBAC_GUIDE.md)
 */
object UserSecretListInfo : PanelInfo {
    override val id = PanelId("user-secret-list", 25)
    override val displayName = "My Secrets"
    override val icon = FeatherIcons.Key
    override val defaultSlotPosition = right.top.bottom
}
