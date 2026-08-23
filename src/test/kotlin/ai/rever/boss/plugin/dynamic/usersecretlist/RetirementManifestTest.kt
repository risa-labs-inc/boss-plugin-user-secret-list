package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.PanelId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The manifest facts this retirement rests on, and the panel identity the notice is reached by.
 *
 * All of them fail silently: a dropped or optional-ised dependency just means no install
 * prompt, a raised floor means the update never reaches the hosts that still show the old
 * panel, and a changed panel id means a saved sidebar layout stops resolving the one panel that
 * explains where the user's secrets went. AGENTS.md lists the three invariants; the fourth
 * (no MCP tools) is guaranteed by the tool file being deleted, which is a compile-time fact.
 *
 * There is deliberately no test of `register()`: `PluginContext` has 71 abstract members.
 *
 * Read from the classpath, filtered on `pluginId`, for the reason [RetiredPluginVersion]
 * documents: every BOSS plugin ships this resource at the same path.
 */
class RetirementManifestTest {
    @Test
    fun `Secret Manager is a required dependency, not an optional one`() {
        // Both halves matter, and a substring search over the whole file proves neither: the id
        // also appears in prose, and flipping `optional` to true quietly turns the host's
        // "needs Secret Manager, which is not installed" prompt into "works without it".
        val block =
            assertNotNull(
                Regex("\"dependencies\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
                    .find(manifest)
                    ?.groupValues
                    ?.get(1),
                "the manifest declares no dependencies block: $manifest",
            )

        assertTrue(
            block.contains("\"ai.rever.boss.plugin.dynamic.secretmanager\""),
            "the dependencies block does not name Secret Manager: $block",
        )
        assertEquals(
            "false",
            Regex("\"optional\"\\s*:\\s*(true|false)").find(block)?.groupValues?.get(1),
            "the Secret Manager dependency is optional, so the host will not offer to install it: $block",
        )
    }

    @Test
    fun `the dependency declares no version, because processResources would rewrite it`() {
        // processResources stamps the build version into every line matching "version": "...",
        // and it is line-based - so a `"version": "*"` inside the dependencies block came out of
        // the jar as `"version": "1.2.5"`, i.e. "requires secret-manager 1.2.5", which is this
        // plugin's version and nonsense as a requirement. PluginDependency.version defaults to
        // "*" and the host ignores it anyway, so the line is better absent.
        //
        // Only visible by reading the built jar; the committed file looked fine.
        val block =
            assertNotNull(
                Regex("\"dependencies\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
                    .find(manifest)
                    ?.groupValues
                    ?.get(1),
                "no dependencies block",
            )
        assertTrue(
            !block.contains("\"version\""),
            "the dependency declares a version, which processResources will rewrite: $block",
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

    @Test
    fun `the panel keeps the id and slot a saved sidebar layout resolves`() {
        // The user has had this panel in this slot since their first run. Move it and the notice
        // explaining where their secrets went is the thing that disappears.
        assertEquals(PanelId("user-secret-list", 25), UserSecretListInfo.id)
        assertEquals(right.top.bottom, UserSecretListInfo.defaultSlotPosition)
    }

    @Test
    fun `the manifest panel block agrees with the panel id`() {
        // Two independent copies of one fact: PanelId's order and the manifest's priority.
        // Nothing else notices if they drift.
        assertEquals("25", value("priority"))
        assertEquals("right_top_bottom", value("position"))
    }

    @Test
    fun `the reported version is the built one, not a literal`() {
        // The class used to say 1.0.5 while the manifest said 1.2.5. This is the release whose
        // version decides whether a host retires the plugin, so a second source of truth for it
        // is worth a test rather than a comment.
        //
        // Asserted against the *Gradle* version, not the bundled manifest: both the class and
        // the manifest read the same file, so comparing them to each other passes for any value
        // in it. This way a processResources that did not stamp fails too.
        val expected =
            assertNotNull(
                System.getProperty("boss.plugin.expectedVersion"),
                "boss.plugin.expectedVersion is not set - see the Test task in build.gradle.kts",
            )
        assertEquals(expected, value("version"), "processResources did not stamp the packaged manifest")
        assertEquals(expected, UserSecretListDynamicPlugin().version, "the class reports a stale literal")
    }

    private companion object {
        val manifest: String =
            checkNotNull(
                RetirementManifestTest::class.java.classLoader
                    .getResources("META-INF/boss-plugin/plugin.json")
                    .toList()
                    .map { it.readText() }
                    .firstOrNull { it.contains("\"${RetiredPluginVersion.PLUGIN_ID}\"") },
            ) { "no plugin.json on the classpath declares this plugin's id" }

        /** Non-null by construction: `assertNotNull` returns the value, so callers get a `String`. */
        fun value(key: String): String =
            assertNotNull(
                Regex("\"$key\"\\s*:\\s*\"?([^\",}\\s]+)\"?").find(manifest)?.groupValues?.get(1),
                "$key is missing from the manifest",
            )
    }
}
