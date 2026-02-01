package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.SecretDataProvider
import kotlinx.coroutines.CoroutineScope

/**
 * My Secrets dynamic plugin - Loaded from external JAR.
 *
 * View your secrets and shared credentials (read-only).
 */
class UserSecretListDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.usersecretlist"
    override val displayName: String = "My Secrets (Dynamic)"
    override val version: String = "1.0.3"
    override val description: String = "View your secrets and shared credentials"
    override val author: String = "Risa Labs"
    override val url: String = "https://github.com/risa-labs-inc/boss-plugin-user-secret-list"

    private var secretDataProvider: SecretDataProvider? = null
    private var pluginScope: CoroutineScope? = null

    override fun register(context: PluginContext) {
        // Capture providers from context
        secretDataProvider = context.secretDataProvider
        pluginScope = context.pluginScope

        context.panelRegistry.registerPanel(UserSecretListInfo) { ctx, panelInfo ->
            UserSecretListComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                secretDataProvider = secretDataProvider,
                scope = pluginScope ?: error("Plugin scope not available")
            )
        }
    }
}
