package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.PanelId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        // `optional: false` is safe only because a missing required dependency has never blocked
        // *loading*, and that had to hold on the OLDEST host this ships to, not just current
        // main - the floor is deliberately 9.2.20. Checked against the tags rather than assumed:
        // at v9.2.20 the only live read of manifest.dependencies is checkCanUnload (refusing to
        // unload a dependency, not to load a dependent); v9.3.20 has none in composeApp at all;
        // v9.4.15 and v9.4.30 have PluginDependencyResolution and MissingDependencyReporter,
        // both of which report. `PluginDependencyResolver` was never constructed anywhere in the
        // repo's history. So the pointer panel renders even if the user declines the install.
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
    fun `the manifest names the class the host will load`() {
        // A second copy of a Kotlin FQN that no compiler checks. Rename or move the class and you
        // get a green build, a green test run, and a plugin the host cannot instantiate - so the
        // notice simply never appears. Same silent-failure class as the other assertions here.
        //
        // Now load-bearing twice over: RetiredPluginVersion picks its own manifest off this
        // field, so a drift here also makes the reported version fall back to unknown.
        assertEquals(UserSecretListDynamicPlugin::class.qualifiedName, value("mainClass"))
        assertEquals(RetiredPluginVersion.MAIN_CLASS, value("mainClass"))
    }

    @Test
    fun `a manifest that only depends on this plugin is not mistaken for it`() {
        // This release establishes the pattern of one manifest naming another plugin's id, in a
        // dependencies block. A bare substring match is satisfied by that - and so is
        // `"pluginId": "<id>"`, because a dependency entry uses the SAME key, which is how the
        // obvious one-line fix for the substring bug still had the bug. mainClass is the field a
        // dependency entry cannot carry.
        val dependsOnUs =
            """{"pluginId":"ai.rever.boss.plugin.dynamic.other","version":"9.9.9",
               "dependencies":[{"pluginId":"${RetiredPluginVersion.PLUGIN_ID}"}]}"""

        assertTrue(dependsOnUs.contains("\"${RetiredPluginVersion.PLUGIN_ID}\""), "precondition: it names us")
        assertTrue(
            Regex("\"pluginId\"\\s*:\\s*\"${RetiredPluginVersion.PLUGIN_ID}\"").containsMatchIn(dependsOnUs),
            "precondition: a dependency entry uses the pluginId key too, so that field is not enough",
        )
        assertFalse(
            RetiredPluginVersion.OURS.containsMatchIn(dependsOnUs),
            "a manifest that only depends on this plugin was matched as being it",
        )
        assertTrue(RetiredPluginVersion.OURS.containsMatchIn(manifest), "our own manifest no longer matches")

        // The selection, not just the pattern. With only our own manifest on the test classpath
        // a reader matching a bare substring passes either way, so this puts the imposter first -
        // which is the ordering that actually breaks it, since resource lookup is parent-first.
        assertEquals(
            manifest,
            RetiredPluginVersion.selectOwnManifest(sequenceOf(dependsOnUs, manifest)),
            "the reader picked a manifest that merely depends on this plugin",
        )
        assertEquals(
            value("version"),
            RetiredPluginVersion.selectOwnManifest(sequenceOf(dependsOnUs, manifest))
                ?.let { RetiredPluginVersion.versionIn(it) },
            "the version came from the wrong manifest",
        )
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
                    // The pluginId FIELD, not a substring: a manifest that merely declares a
                    // dependency on this plugin mentions the id too, so the bare `contains` this
                    // replaced could have had a green test reading someone else's manifest.
                    .let { RetiredPluginVersion.selectOwnManifest(it.asSequence()) },
            ) {
                "no plugin.json on the classpath names this plugin's mainClass - see " +
                    "RetiredPluginVersion.OURS, and note the manifest and the constant must agree"
            }

        /** Non-null by construction: `assertNotNull` returns the value, so callers get a `String`. */
        fun value(key: String): String =
            assertNotNull(
                Regex("\"$key\"\\s*:\\s*\"?([^\",}\\s]+)\"?").find(manifest)?.groupValues?.get(1),
                "$key is missing from the manifest",
            )
    }
}
