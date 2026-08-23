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
 * **Enumerate `getResources`, then filter on `pluginId`.** `getResourceAsStream` is the wrong
 * lookup: every BOSS plugin ships `plugin.json` at this exact path and resource lookup is
 * parent-first, so a neighbour's manifest can win and this plugin would report someone else's
 * version. The sibling `secret-manager` plugin documents the same trap at more length, and
 * additionally prefers the jar its own class came from - overkill for a pointer panel, since
 * the `pluginId` filter already excludes every other plugin's copy.
 *
 * Falls back to the manifest's own committed placeholder rather than throwing: a plugin that
 * refuses to construct because it cannot introspect its own version would turn a cosmetic
 * problem into an unloadable plugin.
 */
internal object RetiredPluginVersion {
    const val PLUGIN_ID = "ai.rever.boss.plugin.dynamic.usersecretlist"

    private const val MANIFEST_PATH = "META-INF/boss-plugin/plugin.json"
    private const val UNKNOWN = "0.0.0-unknown"

    private val VERSION = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")

    fun read(): String =
        runCatching {
            val loader = RetiredPluginVersion::class.java.classLoader ?: return@runCatching null
            loader
                .getResources(MANIFEST_PATH)
                .asSequence()
                .mapNotNull { url -> runCatching { url.readText() }.getOrNull() }
                .firstOrNull { it.contains("\"$PLUGIN_ID\"") }
                ?.let { VERSION.find(it)?.groupValues?.get(1) }
        }.getOrNull() ?: UNKNOWN
}
