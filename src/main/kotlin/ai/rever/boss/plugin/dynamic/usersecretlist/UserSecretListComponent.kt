package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.SecretDataProvider
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope

/**
 * My Secrets panel component (Dynamic Plugin)
 *
 * Provides read-only view of user's secrets and shared credentials.
 */
class UserSecretListComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val secretDataProvider: SecretDataProvider?,
    private val scope: CoroutineScope
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        UserSecretListContent(
            secretDataProvider = secretDataProvider,
            scope = scope
        )
    }
}
