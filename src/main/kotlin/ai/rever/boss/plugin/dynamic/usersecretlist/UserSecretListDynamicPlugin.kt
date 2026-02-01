package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * My Secrets dynamic plugin - Loaded from external JAR.
 *
 * View your secrets and shared credentials
 */
class UserSecretListDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.usersecretlist"
    override val displayName: String = "My Secrets (Dynamic)"
    override val version: String = "1.0.0"
    override val description: String = "View your secrets and shared credentials"
    override val author: String = "Risa Labs"
    override val url: String = "https://github.com/risa-labs-inc/boss-plugin-user-secret-list"

    override fun register(context: PluginContext) {
        context.panelRegistry.registerPanel(UserSecretListInfo) { ctx, panelInfo ->
            UserSecretListComponent(ctx, panelInfo)
        }
    }
}
