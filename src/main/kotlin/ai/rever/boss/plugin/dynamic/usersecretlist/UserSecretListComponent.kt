package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope

/**
 * What is left of the My Secrets panel: a notice saying where its list went.
 *
 * No ViewModel and no [ai.rever.boss.plugin.api.SecretDataProvider]. This component used to
 * hold a list of decrypted secrets; now it reads nothing at all, which is the point - the
 * retired plugin should not keep a second copy of the vault in memory beside the panel that
 * replaced it.
 */
internal class UserSecretListComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    /**
     * Lets the notice offer Open or Install. Null when the host gave us neither a panel-event
     * provider nor a loader delegate, in which case the notice is text only.
     */
    private val link: SecretManagerLink? = null,
    /** Lets the notice remove this plugin. Null when the host gave us no loader delegate. */
    private val uninstall: SelfUninstall? = null,
    /**
     * The scope the uninstall runs on. Deliberately the *plugin* scope, not the composition's:
     * the last step of an uninstall unregisters this panel, so a composition-scoped coroutine
     * would be cancelled partway - and a cancelled uninstall that has not yet deleted the jar
     * brings the plugin back at the next launch.
     */
    private val workScope: CoroutineScope? = null,
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        MovedNotice(link, uninstall, workScope)
    }
}
