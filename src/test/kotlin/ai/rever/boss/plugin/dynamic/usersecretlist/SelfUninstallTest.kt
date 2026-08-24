package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.LoadedPluginInfo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Removing this plugin from inside it.
 *
 * The ordering test is the one that matters. `disablePlugin` unregisters the panel, which cancels
 * anything running on the composition's scope - so a disable that happens before the jar is
 * deleted leaves the jar behind, and the plugin is back at the next launch. That is the single
 * way this feature fails silently, and it looks fine in review either way round.
 */
class SelfUninstallTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `the jar and its sidecar go, and the plugin is disabled`() =
        runBlocking {
            val jar = File(dir, "boss-plugin-user-secret-list-1.2.7.jar").apply { writeText("jar") }
            val sidecar = File(dir, "boss-plugin-user-secret-list-1.2.7.jar.sig").apply { writeText("sig") }
            val loader = FakeLoader(jarPath = jar.path)

            val outcome = SelfUninstall(loader).run()

            assertEquals(UninstallOutcome.REMOVED, outcome)
            assertFalse(jar.exists())
            assertFalse(sidecar.exists(), "a surviving signature hard-fails the next install of this version")
            assertEquals(listOf(RetiredPluginVersion.PLUGIN_ID), loader.disabled)
        }

    @Test
    fun `the jar is deleted before the plugin is disabled`() =
        runBlocking {
            // Reversed, the disable unregisters the panel first and cancels the coroutine doing
            // the work - so the jar survives and the plugin returns at the next launch. Recorded
            // as a sequence because both orders leave the same end state in this test's fakes.
            val jar = File(dir, "plugin.jar").apply { writeText("jar") }
            val steps = mutableListOf<String>()
            val loader = FakeLoader(jarPath = jar.path, onDisable = { steps += "disable" })

            SelfUninstall(loader, deleteFile = { steps += "delete"; it.delete() }).run()

            assertEquals(listOf("delete", "disable"), steps)
        }

    @Test
    fun `a jar that cannot be deleted still disables, and says so`() =
        runBlocking {
            // Windows holds a lock on a jar loaded earlier in the process. Disabling is what
            // stops it loading again, so this is still a removal - just not a tidy one.
            File(dir, "plugin.jar").writeText("jar")
            val loader = FakeLoader(jarPath = File(dir, "plugin.jar").path)

            val outcome = SelfUninstall(loader, deleteFile = { false }).run()

            assertEquals(UninstallOutcome.REMOVED, outcome)
            assertEquals(listOf(RetiredPluginVersion.PLUGIN_ID), loader.disabled)
        }

    @Test
    fun `a deleted jar with a failed disable reports that a restart finishes it`() =
        runBlocking {
            // The honest half-way state: it cannot load again, but this session still has the
            // panel. Saying "removed" flat would be a lie the user can see through.
            val jar = File(dir, "plugin.jar").apply { writeText("jar") }
            val loader = FakeLoader(jarPath = jar.path, disableSucceeds = false)

            val outcome = SelfUninstall(loader).run()

            assertEquals(UninstallOutcome.REMOVED_RESTART_REQUIRED, outcome)
            assertFalse(jar.exists())
        }

    @Test
    fun `neither half working is a failure the user is told about`() =
        runBlocking {
            File(dir, "plugin.jar").writeText("jar")
            val loader = FakeLoader(jarPath = File(dir, "plugin.jar").path, disableSucceeds = false)

            val outcome = SelfUninstall(loader, deleteFile = { false }).run()

            assertEquals(UninstallOutcome.FAILED, outcome)
        }

    @Test
    fun `an unknown jar path is not a failure`() =
        runBlocking {
            // Disabling alone still stops it loading. Reporting failure here would have the user
            // pressing a button that says it did not work when it did.
            val loader = FakeLoader(jarPath = "")

            val outcome = SelfUninstall(loader).run()

            assertEquals(UninstallOutcome.REMOVED, outcome)
            assertEquals(listOf(RetiredPluginVersion.PLUGIN_ID), loader.disabled)
        }

    @Test
    fun `no loader delegate means the button cannot pretend to work`() =
        runBlocking {
            assertEquals(UninstallOutcome.FAILED, SelfUninstall(loader = null).run())
        }

    @Test
    fun `a loader that throws is a failure, not a crash and not a false success`() =
        runBlocking {
            // The first version of this reported REMOVED_RESTART_REQUIRED here: a throwing
            // getLoadedPlugins left the jar path unknown, which is treated as "nothing to
            // delete", and the failed disable then looked like the only problem. Nothing at all
            // had happened, and the user was told it had.
            val outcome = SelfUninstall(ThrowingLoader()).run()

            assertEquals(UninstallOutcome.FAILED, outcome)
        }

    @Test
    fun `only this plugin is disabled, never another`() =
        runBlocking {
            // It reads its own row out of getLoadedPlugins by id; a neighbour's jar must not be
            // the one deleted.
            val ours = File(dir, "ours.jar").apply { writeText("ours") }
            val theirs = File(dir, "theirs.jar").apply { writeText("theirs") }
            val loader = FakeLoader(jarPath = ours.path, otherPluginJar = theirs.path)

            SelfUninstall(loader).run()

            assertFalse(ours.exists())
            assertTrue(theirs.exists(), "deleted another plugin's jar")
        }

    private class FakeLoader(
        private val jarPath: String,
        private val disableSucceeds: Boolean = true,
        private val otherPluginJar: String? = null,
        private val onDisable: () -> Unit = {},
    ) : StubLoader() {
        val disabled = mutableListOf<String>()

        override fun getLoadedPlugins(): List<LoadedPluginInfo> =
            listOfNotNull(
                otherPluginJar?.let {
                    LoadedPluginInfo(
                        pluginId = "ai.rever.boss.plugin.dynamic.other",
                        displayName = "Other",
                        version = "1.0.0",
                        jarPath = it,
                    )
                },
                LoadedPluginInfo(
                    pluginId = RetiredPluginVersion.PLUGIN_ID,
                    displayName = "My Secrets",
                    version = "1.2.7",
                    jarPath = jarPath,
                ),
            )

        override suspend fun disablePlugin(pluginId: String): Boolean {
            onDisable()
            if (disableSucceeds) disabled += pluginId
            return disableSucceeds
        }
    }

    private class ThrowingLoader : StubLoader() {
        override fun getLoadedPlugins(): List<LoadedPluginInfo> = throw NoSuchMethodError("getLoadedPlugins")

        override suspend fun disablePlugin(pluginId: String): Boolean = throw NoSuchMethodError("disablePlugin")
    }
}
