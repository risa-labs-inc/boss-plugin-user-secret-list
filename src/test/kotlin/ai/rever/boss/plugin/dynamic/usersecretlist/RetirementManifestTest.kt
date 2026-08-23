package ai.rever.boss.plugin.dynamic.usersecretlist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The two manifest facts this retirement rests on.
 *
 * Neither is exercised by anything else, and both fail silently: a dropped dependency just
 * means no install prompt, and a raised floor just means the update never reaches the hosts
 * that still show the old panel. There is deliberately no test of `register()` itself -
 * `PluginContext` has 71 abstract members, and the thing worth asserting about it (that no MCP
 * tools are contributed any more) is guaranteed by the tool file being deleted, which is a
 * compile-time fact rather than a runtime one.
 *
 * Read from the classpath, filtered on `pluginId`, for the reason `PluginVersionSource`
 * documents in the sibling plugin: every BOSS plugin ships this resource at the same path.
 */
class RetirementManifestTest {
    @Test
    fun `the manifest points at Secret Manager as a dependency`() {
        // What turns "your panel says it moved" into "the host offers to install the thing it
        // moved to": PluginUpdateBridge reports missing dependencies on update, so a user who
        // has this plugin and not Secret Manager gets a prompt rather than a dead end.
        assertTrue(
            manifest.contains("ai.rever.boss.plugin.dynamic.secretmanager"),
            "the manifest no longer names Secret Manager: $manifest",
        )
    }

    @Test
    fun `the host floor stays low enough to reach every install`() {
        // This version has to land everywhere the old panel is, including old hosts. Raising
        // either floor means the updater skips exactly the installs that still show the list
        // this release exists to retire.
        assertEquals("9.2.20", value("minBossVersion"))
        assertEquals("1.0.20", value("apiVersion"))
    }

    private companion object {
        val manifest: String =
            checkNotNull(
                RetirementManifestTest::class.java.classLoader
                    .getResources("META-INF/boss-plugin/plugin.json")
                    .toList()
                    .map { it.readText() }
                    .firstOrNull { it.contains("\"ai.rever.boss.plugin.dynamic.usersecretlist\"") },
            ) { "no plugin.json on the classpath declares this plugin's id" }

        fun value(key: String): String? =
            Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(manifest)?.groupValues?.get(1).also {
                assertNotNull(it, "$key is missing from the manifest")
            }
    }
}
