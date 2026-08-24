package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginLoaderDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

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
    override val pluginId: String = RetiredPluginVersion.PLUGIN_ID
    override val displayName: String = "My Secrets (Dynamic)"

    /**
     * From the bundled manifest, not a literal: the literal said `1.0.5` against a manifest
     * saying `1.2.5`. See [RetiredPluginVersion].
     */
    override val version: String = RetiredPluginVersion.read()
    override val description: String = "Retired - now the \"Shared with me\" section of Secret Manager"
    override val author: String = "Risa Labs"
    override val url: String = "https://github.com/risa-labs-inc/boss-plugin-user-secret-list"

    override fun register(context: PluginContext) {
        // Read once here rather than per panel construction: the providers do not change for the
        // life of the plugin, and the reflective capability probe should not run per composition.
        val link = SecretManagerLink.from(context)
        val uninstall = SelfUninstall(runCatching { context.getPluginAPI(PluginLoaderDelegate::class.java) }.getOrNull())
        // The plugin scope, so an uninstall is not cancelled by the panel it removes. Falls back
        // to a scope of our own rather than dropping the button on a host that gives us none.
        val workScope = context.pluginScope ?: CoroutineScope(Dispatchers.Main)

        // The panel id and icon are unchanged, so a saved sidebar layout still resolves it and
        // the user finds the notice where the list used to be.
        context.panelRegistry.registerPanel(UserSecretListInfo) { ctx, panelInfo ->
            UserSecretListComponent(ctx, panelInfo, link, uninstall, workScope)
        }
    }
}
