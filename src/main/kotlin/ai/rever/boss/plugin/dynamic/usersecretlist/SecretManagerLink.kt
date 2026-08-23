package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.PanelEventProvider
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginLoaderDelegate

/** What the notice can offer the user, given what this machine and this host can actually do. */
internal enum class MovedNoticeAction {
    /** Secret Manager is here and this host can reveal panels. */
    OPEN_SECRET_MANAGER,

    /** It is not installed, and this host can at least open the Toolbox to get it. */
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
): MovedNoticeAction =
    when {
        !canOpenPanels -> MovedNoticeAction.DESCRIBE_ONLY
        secretManagerInstalled -> MovedNoticeAction.OPEN_SECRET_MANAGER
        else -> MovedNoticeAction.INSTALL_FROM_TOOLBOX
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

    internal companion object {
        const val SECRET_MANAGER_ID = "ai.rever.boss.plugin.dynamic.secretmanager"

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
