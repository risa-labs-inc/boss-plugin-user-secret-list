package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

/**
 * What is left of the My Secrets panel: a notice saying where its list went.
 *
 * No ViewModel and no [ai.rever.boss.plugin.api.SecretDataProvider]. This component used to
 * hold a list of decrypted secrets; now it reads nothing at all, which is the point - the
 * retired plugin should not keep a second copy of the vault in memory beside the panel that
 * replaced it.
 */
class UserSecretListComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        MovedNotice()
    }
}
