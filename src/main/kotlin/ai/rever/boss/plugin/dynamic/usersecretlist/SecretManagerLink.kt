package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.PanelEventProvider
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginLoaderDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

/** What the notice can offer the user, given what this machine and this host can actually do. */
internal enum class MovedNoticeAction {
    /** Secret Manager is here and this host can reveal panels. */
    OPEN_SECRET_MANAGER,

    /**
     * Not installed, and the Toolbox is new enough to be asked to install it: the press raises
     * the Toolbox's own confirm dialog, which names the plugin from the store and installs on the
     * answer. The good case.
     */
    INSTALL_VIA_TOOLBOX_PROMPT,

    /** Not installed, and the best available route is opening the Toolbox for the user to look. */
    INSTALL_FROM_TOOLBOX,

    /** Nothing to offer: an older host, where a button would do nothing. Say where to look. */
    DESCRIBE_ONLY,
}

/**
 * Which button the notice shows.
 *
 * A pure function, so the one decision in this feature is testable without a host: the plugin
 * cannot be run in a test at all (`PluginContext` has 71 abstract members), and the two inputs
 * come from places that are awkward to fake and easy to get backwards.
 *
 * [canOpenPanels] is not a nicety. `PanelEventProvider.openPanel` arrived in api **1.0.57** and
 * this plugin's floor is deliberately **1.0.20**, because the release has to reach every host that
 * still shows the old panel. On a host below that, a button is a control that cannot work, and the
 * notice is better off saying where to look than offering one - see
 * [SecretManagerLink.canOpenPanels].
 */
internal fun movedNoticeAction(
    secretManagerInstalled: Boolean,
    canOpenPanels: Boolean,
    canAskToolboxToInstall: Boolean,
): MovedNoticeAction =
    when {
        // Opening needs openPanel; asking the Toolbox to install does not, so the install route
        // can be live on a host where the open route is not. Kept independent for that reason,
        // even though a Toolbox new enough to be asked implies a host new enough to open panels.
        secretManagerInstalled && canOpenPanels -> MovedNoticeAction.OPEN_SECRET_MANAGER
        secretManagerInstalled -> MovedNoticeAction.DESCRIBE_ONLY
        canAskToolboxToInstall -> MovedNoticeAction.INSTALL_VIA_TOOLBOX_PROMPT
        canOpenPanels -> MovedNoticeAction.INSTALL_FROM_TOOLBOX
        else -> MovedNoticeAction.DESCRIBE_ONLY
    }

/**
 * The notice's two actions, and the api-version guarding they need, in one place.
 *
 * **Everything newer than this plugin's 1.0.20 floor is confined here.** `ai.rever.boss.plugin.api`
 * is a shared package served parent-first from the host's own api layer, so a method the host's
 * copy predates is a `NoSuchMethodError` (a `LinkageError`) at the call, not a compile error - and
 * this plugin exists to keep loading on exactly those hosts. `openPanel` is such a method.
 *
 * **There is deliberately no install call.** No plugin-facing api installs another plugin: the
 * host's own installer resolves a store row to a jar, which is why the Toolbox's deep-link handler
 * exists for web pages. So "Install" opens the Toolbox, where the user can install it - the button
 * says so rather than implying a one-press install this cannot perform.
 */
internal class SecretManagerLink(
    private val loader: PluginLoaderDelegate?,
    private val panels: PanelEventProvider?,
    private val windowId: String?,
    /**
     * The method names the host's provider actually has, injected so [canOpenPanels] is testable.
     *
     * It cannot be tested through a hand-written fake: `openPanel` has a default body, and Kotlin
     * synthesises an override into every implementing class - so a fake that does *not* override
     * it still reports the method, and the "old host" case is unreachable from a test. Production
     * passes real reflection; a test passes the two sets that matter.
     */
    private val methodNamesOf: (Any) -> Set<String> = { target ->
        runCatching { target.javaClass.methods.map { it.name }.toSet() }.getOrDefault(emptySet())
    },
    /** Whether this JVM can hand a URL to the platform. Injected so the route is testable. */
    private val browseSupported: () -> Boolean = {
        runCatching {
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        }.getOrDefault(false)
    },
    /** Hands [INSTALL_DEEP_LINK] to the platform, which routes it back into this instance. */
    private val browse: (String) -> Boolean = { url ->
        runCatching { Desktop.getDesktop().browse(URI(url)); true }.getOrDefault(false)
    },
) {
    /**
     * Whether this host can reveal a panel at all.
     *
     * **Reflective, because the honest probe has a side effect.** Calling `openPanel` to find out
     * whether it exists reveals a panel, and this is answered while the plugin registers - so the
     * probe itself would pop the notice open unbidden. Asking the host's implementation class
     * whether the method exists costs nothing and changes nothing.
     *
     * Checking the *implementation* rather than the interface is the point: `openPanel` has a
     * default no-op body in the api, so on a host whose api predates it the method is simply
     * absent from the class the host registered. The current host overrides it (verified in
     * `PanelEventProviderImpl`), so presence means it does something.
     *
     * Matched by name only: it is a suspend function, so its JVM signature carries a trailing
     * `Continuation` that a by-signature lookup would have to reproduce exactly.
     */
    fun canOpenPanels(): Boolean {
        val provider = panels ?: return false
        if (windowId == null) return false
        return OPEN_PANEL in methodNamesOf(provider)
    }

    /**
     * Whether Secret Manager is here **and usable**.
     *
     * Deliberately not `isPluginLoaded`, which was the first version of this and is wrong in a
     * way that shows: `DynamicPluginManager.disablePlugin` flips the state to `DISABLED` and
     * never calls `pluginLoader.unloadPlugin`, so a plugin the user has switched off is still in
     * `getLoadedPlugins()`. The notice would then offer "Open Secret Manager" and reveal a panel
     * that is not registered - a button that does nothing, which is the exact failure the
     * capability probe exists to avoid.
     *
     * `healthy` and `isIncompatible` come along for the same reason: a plugin the host has
     * recorded as crashed or binary-incompatible has no panel to reveal either.
     */
    fun secretManagerInstalled(): Boolean =
        runCatching {
            val loaded = loader?.getLoadedPlugins() ?: return@runCatching false
            loaded.any {
                it.pluginId == SECRET_MANAGER_ID && it.isEnabled && it.healthy && !it.isIncompatible
            }
        }.getOrDefault(false)

    /** Reveal Secret Manager's panel. False when this host cannot, so the caller can say so. */
    suspend fun openSecretManager(): Boolean = reveal(SECRET_MANAGER_PANEL)

    /** Reveal the Toolbox, where Secret Manager can be installed. */
    suspend fun openToolbox(): Boolean = reveal(TOOLBOX_PANEL)

    /**
     * Whether the Toolbox can be *asked* to install Secret Manager, rather than merely opened.
     *
     * Needs its deep-link handler, which shipped in Toolbox **1.9.14** - so the version is
     * checked rather than assumed, and an older Toolbox falls back to being opened.
     */
    fun canAskToolboxToInstall(): Boolean {
        if (!browseSupported()) return false
        val toolbox =
            runCatching {
                loader?.getLoadedPlugins()?.firstOrNull { it.pluginId == TOOLBOX_ID }
            }.getOrNull() ?: return false
        if (!toolbox.isEnabled || !toolbox.healthy || toolbox.isIncompatible) return false
        return atLeast(toolbox.version, TOOLBOX_WITH_INSTALL_DEEP_LINK)
    }

    /**
     * Asks the Toolbox to install Secret Manager, which raises **its** confirm dialog: the plugin
     * is named from the store rather than from the link, and nothing installs without the press.
     *
     * The route is a `boss://` deep link handed to the OS, because there is no plugin-facing api
     * that installs a plugin and no way to dispatch a deep link either. The scheme is registered
     * to BOSS, so the URL comes back into this same running instance through
     * `SingleInstanceManager` and reaches the Toolbox's `DeepLinkActionHandler`. That handler
     * exists precisely so a *web page* can offer Install without being trusted about what is
     * installed - which makes it the right door for a plugin that cannot be trusted about it
     * either: it refreshes its own view first, and refuses to reinstall something already here.
     *
     * On `Dispatchers.IO` because `Desktop.browse` hands off to the platform and can block, and
     * the caller's scope may be the main one.
     */
    suspend fun askToolboxToInstallSecretManager(): Boolean =
        withContext(Dispatchers.IO) {
            if (!canAskToolboxToInstall()) return@withContext false
            browse(INSTALL_DEEP_LINK)
        }

    private suspend fun reveal(panelId: PanelId): Boolean {
        val provider = panels ?: return false
        val window = windowId ?: return false
        return try {
            provider.openPanel(panelId, window)
            true
        } catch (_: LinkageError) {
            // Host api predates openPanel (1.0.57). Not an error: this plugin's floor is 1.0.20
            // on purpose, and the notice degrades to text.
            false
        }
    }

    /**
     * Compares release cores only (`1.9.14`), ignoring anything after a `-` or `+`.
     *
     * Hand-rolled because `SemanticVersion` lives in the host's `plugin-dependency` module, not on
     * the plugin api - and a prerelease of the release that added the handler does have the
     * handler, so ignoring the suffix is the behaviour we want anyway.
     */
    private fun atLeast(
        version: String,
        minimum: List<Int>,
    ): Boolean {
        val parts =
            version
                .takeWhile { it != '-' && it != '+' }
                .split('.')
                .map { it.toIntOrNull() ?: return false }
        minimum.forEachIndexed { index, floor ->
            val here = parts.getOrNull(index) ?: 0
            if (here != floor) return here > floor
        }
        return true
    }

    internal companion object {
        const val SECRET_MANAGER_ID = "ai.rever.boss.plugin.dynamic.secretmanager"

        const val TOOLBOX_ID = "ai.rever.boss.plugin.dynamic.pluginmanager"

        /** The Toolbox release that first carried `PluginDeepLinkActions`. */
        val TOOLBOX_WITH_INSTALL_DEEP_LINK = listOf(1, 9, 14)

        /**
         * `action=install` rather than `open`: the Toolbox decides from what it finds installed,
         * so the link cannot be wrong about the outcome - only about what it asks for.
         */
        const val INSTALL_DEEP_LINK =
            "boss://plugin?id=$TOOLBOX_ID&action=install&plugin=$SECRET_MANAGER_ID"

        /** Matched by name: it is a suspend function, so its JVM signature carries a Continuation. */
        const val OPEN_PANEL = "openPanel"

        /**
         * Secret Manager's panel, with the `defaultOrder` its own `PanelInfo` declares.
         *
         * The order is part of the id, and the host normalises a plugin-supplied one through
         * `resolveRegisteredPanelId` - but only by `panelId` + `pluginId`, so the number here is
         * matched on nothing and a wrong one would still resolve. Kept accurate anyway.
         */
        val SECRET_MANAGER_PANEL = PanelId("secret-manager", 24)

        /** The Toolbox (plugin store client). Its `PanelInfo` declares order 6. */
        val TOOLBOX_PANEL = PanelId("plugin-manager", 6)

        /** Reads the two providers off the context. Null-safe: both may be absent. */
        fun from(context: PluginContext): SecretManagerLink =
            SecretManagerLink(
                loader = runCatching { context.getPluginAPI(PluginLoaderDelegate::class.java) }.getOrNull(),
                panels = runCatching { context.panelEventProvider }.getOrNull(),
                windowId = runCatching { context.windowId }.getOrNull(),
            )
    }
}
