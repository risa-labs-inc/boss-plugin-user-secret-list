package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Retired. This plugin's list is now the "Shared with me" section of Secret Manager
 * (`ai.rever.boss.plugin.dynamic.secretmanager`).
 *
 * The two panels sat next to each other in the sidebar reading the same vault, and both listed
 * the caller's own secrets - this one through `getUserSecretsWithSharingInfo`, Secret Manager
 * through `getUserSecrets`. One panel with two sections, split on how each secret reached you,
 * is the same information without the overlap.
 *
 * What is left is a pointer. It registers one panel that says where the list went, and
 * **contributes no MCP tools**: `my_secrets_list` and `my_secret_get` moved to Secret Manager
 * under the same names, and a second copy of `my_secret_get` is the exact shape of the bug that
 * once let it read the AI provider keys `secret_get` withholds - one gate, one implementation.
 *
 * `plugin.json` declares Secret Manager as a dependency, so a user who has this plugin and not
 * that one gets the host's install prompt on update rather than a dead sidebar icon.
 * Dependencies are not enforced at load time, so the pointer still renders if they decline.
 *
 * The manifest floor stays at api 1.0.20 / BOSS 9.2.20 deliberately: this version has to reach
 * every installed host, including the old ones, or the panel it is replacing stays.
 */
class UserSecretListDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.usersecretlist"
    override val displayName: String = "My Secrets (Dynamic)"
    override val version: String = "1.0.5"
    override val description: String = "Retired - now the \"Shared with me\" section of Secret Manager"
    override val author: String = "Risa Labs"
    override val url: String = "https://github.com/risa-labs-inc/boss-plugin-user-secret-list"

    override fun register(context: PluginContext) {
        // The panel id and icon are unchanged, so a saved sidebar layout still resolves it and
        // the user finds the notice where the list used to be.
        context.panelRegistry.registerPanel(UserSecretListInfo) { ctx, panelInfo ->
            UserSecretListComponent(ctx, panelInfo)
        }
    }
}
