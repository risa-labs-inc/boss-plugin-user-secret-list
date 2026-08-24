package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.LoadedPluginInfo
import ai.rever.boss.plugin.api.PanelEventProvider
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PluginLoaderDelegate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the notice offers, and what it must not offer.
 *
 * The interesting cases are all "this host cannot do that": the floor is api 1.0.20 and
 * `openPanel` arrived in 1.0.57, so on a real fraction of the installs this release is built to
 * reach, a button would be a control that does nothing.
 */
class SecretManagerLinkTest {
    @Test
    fun `an installed Secret Manager gets an Open button`() {
        assertEquals(
            MovedNoticeAction.OPEN_SECRET_MANAGER,
            movedNoticeAction(secretManagerInstalled = true, canOpenPanels = true, canAskToolboxToInstall = true),
        )
    }

    @Test
    fun `a missing Secret Manager gets an Install button`() {
        assertEquals(
            MovedNoticeAction.INSTALL_FROM_TOOLBOX,
            movedNoticeAction(secretManagerInstalled = false, canOpenPanels = true, canAskToolboxToInstall = false),
        )
    }

    @Test
    fun `a Toolbox that can be asked gets the prompt route, not just an open`() {
        // The difference the user sees: a confirm dialog that installs, rather than a panel to go
        // hunting in.
        assertEquals(
            MovedNoticeAction.INSTALL_VIA_TOOLBOX_PROMPT,
            movedNoticeAction(
                secretManagerInstalled = false,
                canOpenPanels = true,
                canAskToolboxToInstall = true,
            ),
        )
    }

    @Test
    fun `the prompt route does not need openPanel`() {
        // Asking the Toolbox goes through a boss:// link, not PanelEventProvider - so it works on
        // a host too old for openPanel, which is the population this plugin exists to reach.
        assertEquals(
            MovedNoticeAction.INSTALL_VIA_TOOLBOX_PROMPT,
            movedNoticeAction(
                secretManagerInstalled = false,
                canOpenPanels = false,
                canAskToolboxToInstall = true,
            ),
        )
    }

    @Test
    fun `an installed Secret Manager on a host that cannot open panels describes instead`() {
        // Never the Install route: it is installed, so offering to install it would be wrong even
        // though the open route is unavailable.
        assertEquals(
            MovedNoticeAction.DESCRIBE_ONLY,
            movedNoticeAction(
                secretManagerInstalled = true,
                canOpenPanels = false,
                canAskToolboxToInstall = true,
            ),
        )
    }

    @Test
    fun `the Toolbox is asked only when its version carries the handler`() {
        // PluginDeepLinkActions shipped in Toolbox 1.9.14. Older ones have nothing listening, so
        // the link would be a press that does nothing.
        assertTrue(linkWithToolbox("1.9.14").canAskToolboxToInstall())
        assertTrue(linkWithToolbox("1.9.21").canAskToolboxToInstall())
        assertTrue(linkWithToolbox("2.0.0").canAskToolboxToInstall())
        assertFalse(linkWithToolbox("1.9.13").canAskToolboxToInstall())
        assertFalse(linkWithToolbox("1.8.99").canAskToolboxToInstall())
        assertFalse(linkWithToolbox("0.9.0").canAskToolboxToInstall())
    }

    @Test
    fun `a prerelease of the release that added the handler counts`() {
        // The suffix is ignored on purpose: 1.9.14-rc.1 is built from the source that has it.
        assertTrue(linkWithToolbox("1.9.14-rc.1").canAskToolboxToInstall())
        assertTrue(linkWithToolbox("1.9.21+build.7").canAskToolboxToInstall())
    }

    @Test
    fun `an unparseable Toolbox version is not asked`() {
        assertFalse(linkWithToolbox("dev").canAskToolboxToInstall())
        assertFalse(linkWithToolbox("").canAskToolboxToInstall())
    }

    @Test
    fun `a disabled or unhealthy Toolbox is not asked`() {
        assertFalse(linkWithToolbox("1.9.21", enabled = false).canAskToolboxToInstall())
        assertFalse(linkWithToolbox("1.9.21", healthy = false).canAskToolboxToInstall())
        assertFalse(linkWithToolbox("1.9.21", incompatible = true).canAskToolboxToInstall())
    }

    @Test
    fun `a JVM that cannot hand a URL to the platform is not asked`() {
        // Headless, or a platform with no BROWSE action: the link would go nowhere.
        assertFalse(linkWithToolbox("1.9.21", browseSupported = false).canAskToolboxToInstall())
    }

    @Test
    fun `asking sends the install deep link for Secret Manager, addressed to the Toolbox`() =
        runBlocking {
            val sent = mutableListOf<String>()
            val link = linkWithToolbox("1.9.21", onBrowse = { sent += it })

            assertTrue(link.askToolboxToInstallSecretManager())

            assertEquals(1, sent.size)
            val url = sent.single()
            assertTrue(url.contains("id=${SecretManagerLink.TOOLBOX_ID}"), url)
            assertTrue(url.contains("action=install"), url)
            assertTrue(url.contains("plugin=${SecretManagerLink.SECRET_MANAGER_ID}"), url)
        }

    @Test
    fun `asking an old Toolbox sends nothing at all`() =
        runBlocking {
            // Better a false return the caller can fall back from than a link nobody handles.
            val sent = mutableListOf<String>()
            val link = linkWithToolbox("1.9.13", onBrowse = { sent += it })

            assertFalse(link.askToolboxToInstallSecretManager())
            assertTrue(sent.isEmpty())
        }

    private fun linkWithToolbox(
        toolboxVersion: String,
        enabled: Boolean = true,
        healthy: Boolean = true,
        incompatible: Boolean = false,
        browseSupported: Boolean = true,
        onBrowse: (String) -> Unit = {},
    ) = SecretManagerLink(
        loader =
            FakeLoader(
                listOf(
                    LoadedPluginInfo(
                        pluginId = SecretManagerLink.TOOLBOX_ID,
                        displayName = "Toolbox",
                        version = toolboxVersion,
                        isEnabled = enabled,
                        healthy = healthy,
                        isIncompatible = incompatible,
                    ),
                ),
            ),
        panels = RecordingPanels(),
        windowId = "w",
        methodNamesOf = { setOf("openPanel") },
        browseSupported = { browseSupported },
        browse = { onBrowse(it); true },
    )

    @Test
    fun `a host that cannot open panels gets no button either way`() {
        // Both rows, because "installed" is not the question on such a host - the button would be
        // dead in both states, and text that says where to look is better than a dead control.
        assertEquals(
            MovedNoticeAction.DESCRIBE_ONLY,
            movedNoticeAction(secretManagerInstalled = true, canOpenPanels = false, canAskToolboxToInstall = false),
        )
        assertEquals(
            MovedNoticeAction.DESCRIBE_ONLY,
            movedNoticeAction(secretManagerInstalled = false, canOpenPanels = false, canAskToolboxToInstall = false),
        )
    }

    @Test
    fun `a host whose provider predates openPanel cannot open panels`() {
        val link = link(methodNames = setOf("closePanel"))

        assertFalse(link.canOpenPanels())
    }

    @Test
    fun `a host whose provider has openPanel can`() {
        val link = link(methodNames = setOf("closePanel", "openPanel"))

        assertTrue(link.canOpenPanels())
    }

    @Test
    fun `no provider and no window id both mean no`() {
        assertFalse(SecretManagerLink(loader = null, panels = null, windowId = "w").canOpenPanels())
        assertFalse(link(windowId = null).canOpenPanels())
    }

    @Test
    fun `openSecretManager reveals Secret Manager's panel`() =
        runBlocking {
            val panels = RecordingPanels()
            val link = link(panels = panels)

            assertTrue(link.openSecretManager())
            assertEquals(listOf(SecretManagerLink.SECRET_MANAGER_PANEL to "w"), panels.opened)
        }

    @Test
    fun `openToolbox reveals the Toolbox`() =
        runBlocking {
            val panels = RecordingPanels()
            val link = link(panels = panels)

            assertTrue(link.openToolbox())
            assertEquals(listOf(SecretManagerLink.TOOLBOX_PANEL to "w"), panels.opened)
        }

    @Test
    fun `a host that throws on openPanel reports failure rather than crashing`() =
        runBlocking {
            // What an old host actually does at the call: openPanel is absent from its parent-first
            // copy of the api, so the call is a NoSuchMethodError. The notice must survive a press.
            val link = link(panels = ThrowingPanels())

            assertFalse(link.openSecretManager())
            assertFalse(link.openToolbox())
        }

    @Test
    fun `a loader that throws is treated as not installed`() {
        // Fails towards Install rather than towards an Open button that goes nowhere.
        val link = link(loader = ThrowingLoader())

        assertFalse(link.secretManagerInstalled())
    }

    @Test
    fun `installed is read from the loader delegate`() {
        assertTrue(link(loader = FakeLoader(loaded(SecretManagerLink.SECRET_MANAGER_ID))).secretManagerInstalled())
        assertFalse(link(loader = FakeLoader(emptyList())).secretManagerInstalled())
    }

    @Test
    fun `a disabled Secret Manager is not offered for opening`() {
        // The trap isPluginLoaded walks into: disablePlugin flips the state and never unloads, so
        // a plugin the user switched off is still "loaded" - and its panel is not registered, so
        // Open would be a button that does nothing.
        val link = link(loader = FakeLoader(loaded(SecretManagerLink.SECRET_MANAGER_ID, enabled = false)))

        assertFalse(link.secretManagerInstalled())
        assertEquals(
            MovedNoticeAction.INSTALL_FROM_TOOLBOX,
            movedNoticeAction(link.secretManagerInstalled(), link.canOpenPanels(), canAskToolboxToInstall = false),
        )
    }

    @Test
    fun `an unhealthy or incompatible Secret Manager is not offered either`() {
        assertFalse(link(loader = FakeLoader(loaded(SecretManagerLink.SECRET_MANAGER_ID, healthy = false))).secretManagerInstalled())
        assertFalse(
            link(loader = FakeLoader(loaded(SecretManagerLink.SECRET_MANAGER_ID, incompatible = true)))
                .secretManagerInstalled(),
        )
    }

    @Test
    fun `another plugin being loaded is not Secret Manager`() {
        assertFalse(link(loader = FakeLoader(loaded("ai.rever.boss.plugin.dynamic.codebase"))).secretManagerInstalled())
    }

    private fun loaded(
        pluginId: String,
        enabled: Boolean = true,
        healthy: Boolean = true,
        incompatible: Boolean = false,
    ) = listOf(
        LoadedPluginInfo(
            pluginId = pluginId,
            displayName = pluginId,
            version = "1.0.0",
            isEnabled = enabled,
            healthy = healthy,
            isIncompatible = incompatible,
        ),
    )

    private fun link(
        loader: PluginLoaderDelegate? = FakeLoader(emptyList()),
        panels: PanelEventProvider? = RecordingPanels(),
        windowId: String? = "w",
        methodNames: Set<String> = setOf("openPanel"),
    ) = SecretManagerLink(loader, panels, windowId, methodNamesOf = { methodNames })

    private class RecordingPanels : PanelEventProvider {
        val opened = mutableListOf<Pair<PanelId, String>>()

        override suspend fun closePanel(
            panelId: PanelId,
            windowId: String,
        ) = Unit

        override suspend fun openPanel(
            panelId: PanelId,
            windowId: String,
        ) {
            opened += panelId to windowId
        }
    }

    private class ThrowingPanels : PanelEventProvider {
        override suspend fun closePanel(
            panelId: PanelId,
            windowId: String,
        ) = Unit

        override suspend fun openPanel(
            panelId: PanelId,
            windowId: String,
        ): Unit = throw NoSuchMethodError("PanelEventProvider.openPanel")
    }

    private class FakeLoader(
        private val loaded: List<LoadedPluginInfo>,
    ) : StubLoader() {
        override fun getLoadedPlugins(): List<LoadedPluginInfo> = loaded

        override fun isPluginLoaded(pluginId: String): Boolean = loaded.any { it.pluginId == pluginId }
    }

    private class ThrowingLoader : StubLoader() {
        override fun getLoadedPlugins(): List<LoadedPluginInfo> = throw NoSuchMethodError("getLoadedPlugins")

        override fun isPluginLoaded(pluginId: String): Boolean = throw NoSuchMethodError("isPluginLoaded")
    }
}

/**
 * The eleven abstract members of `PluginLoaderDelegate` that the two fakes above do not care
 * about. Separate so each fake says only what it is for.
 */
internal abstract class StubLoader : PluginLoaderDelegate {
    /**
     * Derived from [getLoadedPlugins] so a fake only has to say one thing. Subclasses still
     * override it where the point of the fake is that the two disagree.
     */
    override fun isPluginLoaded(pluginId: String): Boolean = getLoadedPlugins().any { it.pluginId == pluginId }

    override suspend fun loadPlugin(jarPath: String) = null

    override suspend fun unloadPlugin(pluginId: String) = false

    override suspend fun reloadPlugin(pluginId: String) = null

    override fun getLoadedPlugins(): List<LoadedPluginInfo> = emptyList()

    override fun getPluginsDirectory() = ""

    override fun getBundledPluginsDirectory() = ""

    override fun isCurrentUserAdmin() = false

    override suspend fun enablePlugin(pluginId: String) = false

    override suspend fun disablePlugin(pluginId: String) = false

    override fun getAccessToken(): String? = null
}
