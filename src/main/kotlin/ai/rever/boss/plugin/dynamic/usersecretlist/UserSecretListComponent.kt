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

    // Created once per panel instance (not per composition), so secrets stay
    // cached across panel switches — reopening renders instantly instead of
    // refetching. Same pattern as the Role Creation plugin; the Refresh
    // button refetches on demand.
    private val viewModel = UserSecretListViewModel(secretDataProvider, scope)

    @Composable
    override fun Content() {
        UserSecretListContent(viewModel)
    }
}
