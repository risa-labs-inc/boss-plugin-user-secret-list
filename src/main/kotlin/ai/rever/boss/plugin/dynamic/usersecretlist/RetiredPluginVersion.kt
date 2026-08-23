package ai.rever.boss.plugin.dynamic.usersecretlist

/**
 * The version this plugin reports, read from the bundled manifest rather than written twice.
 *
 * `DynamicPlugin.version` is abstract, so the class has to report *something*, and a literal
 * there is a second source of truth that goes stale on the very next release: the release
 * workflow bumps `build.gradle.kts` and `processResources` stamps the manifest, and neither
 * touches Kotlin source. It had already drifted to `1.0.5` against a manifest saying `1.2.5`.
 * That matters more than usual here, because this is the release whose version decides whether
 * a host retires the plugin.
 *
 * **Enumerate `getResources`, then match the `pluginId` field.** `getResourceAsStream` is the
 * wrong lookup: every BOSS plugin ships `plugin.json` at this exact path and resource lookup is
 * parent-first, so a neighbour's manifest can win and this plugin would report someone else's
 * version. The sibling `secret-manager` plugin documents the same trap at more length, and
 * additionally prefers the jar its own class came from - overkill here, since matching the field
 * already excludes every other plugin's copy.
 *
 * **The discriminator is `mainClass`, and neither the plugin id nor its `pluginId` field would
 * do.** This release establishes the pattern of one manifest naming *another* plugin's id, in a
 * `dependencies` block - so `contains("\"$PLUGIN_ID\"")` is satisfied by any manifest that merely
 * depends on this plugin, and so is `"pluginId": "<id>"`, because **a dependency entry uses that
 * same key**. Either one would make this class report the *depending* plugin's version the day
 * something declares a dependency on it. `mainClass` names a type that exists only in this jar
 * and cannot appear inside a dependency entry.
 *
 * That matters because `isolationMode` is `out-of-process`, where the classloader probably sees
 * only this jar - but `fallback: in-process` is declared too, and in-process is where
 * neighbours' manifests are on the classpath.
 *
 * `MAIN_CLASS_FIELD` and the manifest are kept in step by
 * `RetirementManifestTest.the manifest names the class the host will load`, which is the same
 * assertion the host relies on to instantiate the plugin at all.
 *
 * Falls back to [UNKNOWN] rather than throwing: a plugin that refuses to construct because it
 * cannot introspect its own version would turn a cosmetic problem into an unloadable one.
 * `0.0.0-unknown` is valid semver, so a host comparing it will not blow up - and it sorts below
 * every real release, so it fails towards "too old" rather than "new enough".
 */
internal object RetiredPluginVersion {
    const val PLUGIN_ID = "ai.rever.boss.plugin.dynamic.usersecretlist"

    private const val MANIFEST_PATH = "META-INF/boss-plugin/plugin.json"
    private const val UNKNOWN = "0.0.0-unknown"

    private val VERSION = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")

    /** The FQN the host instantiates. Unique to this jar's manifest; see the KDoc. */
    const val MAIN_CLASS = "ai.rever.boss.plugin.dynamic.usersecretlist.UserSecretListDynamicPlugin"

    /** `"mainClass": "<ours>"` - the one field a dependency entry cannot carry. */
    val OURS = Regex("\"mainClass\"\\s*:\\s*\"$MAIN_CLASS\"")

    /**
     * Picks this jar's manifest out of everything on the classpath at that path.
     *
     * Separate from [read] and `internal` so the *selection* is testable. Testing the regex
     * alone is not enough: with only our own manifest on the test classpath, a reader that
     * matched a bare substring passed just as well, so the bug this exists to prevent was
     * invisible. The test feeds it an imposter first.
     */
    internal fun selectOwnManifest(documents: Sequence<String>): String? =
        documents.firstOrNull { OURS.containsMatchIn(it) }

    /** The stamped `version` field of a manifest document. */
    internal fun versionIn(document: String): String? = VERSION.find(document)?.groupValues?.get(1)

    /** Read once: this backs a property initialiser on the plugin class, not a per-call lookup. */
    private val version: String by lazy {
        runCatching {
            val loader = RetiredPluginVersion::class.java.classLoader ?: return@runCatching null
            val documents =
                loader
                    .getResources(MANIFEST_PATH)
                    .asSequence()
                    .mapNotNull { url -> runCatching { url.readText() }.getOrNull() }
            selectOwnManifest(documents)?.let { versionIn(it) }
        }.getOrNull() ?: UNKNOWN
    }

    fun read(): String = version
}
