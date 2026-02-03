package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * My Secrets dynamic plugin - Loaded from external JAR.
 *
 * View your secrets and shared credentials (read-only).
 * Uses SecretDataProvider from PluginContext.
 */
class UserSecretListDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.usersecretlist"
    override val displayName: String = "My Secrets (Dynamic)"
    override val version: String = "1.0.3"
    override val description: String = "View your secrets and shared credentials"
    override val author: String = "Risa Labs"
    override val url: String = "https://github.com/risa-labs-inc/boss-plugin-user-secret-list"

    override fun register(context: PluginContext) {
        val secretDataProvider = context.secretDataProvider
        val pluginScope = context.pluginScope ?: CoroutineScope(Dispatchers.Main)

        if (secretDataProvider == null) {
            // Provider not available - register stub
            context.panelRegistry.registerPanel(UserSecretListInfo) { ctx, panelInfo ->
                UserSecretListComponent(ctx, panelInfo, null, pluginScope)
            }
            return
        }

        context.panelRegistry.registerPanel(UserSecretListInfo) { ctx, panelInfo ->
            UserSecretListComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                secretDataProvider = secretDataProvider,
                scope = pluginScope
            )
        }
    }
}
