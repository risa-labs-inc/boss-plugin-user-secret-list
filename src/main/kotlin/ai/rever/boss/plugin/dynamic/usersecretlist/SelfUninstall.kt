package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.PluginLoaderDelegate
import java.io.File

/** What happened when the user asked this plugin to remove itself. */
internal enum class UninstallOutcome {
    /** Gone: the jar is deleted and the panel is unregistered. Nothing to restart for. */
    REMOVED,

    /** The jar is deleted, so it cannot load again - but this session still has the panel. */
    REMOVED_RESTART_REQUIRED,

    /** Neither half worked. The Toolbox can still do it. */
    FAILED,
}

/**
 * Removes this plugin, from inside this plugin.
 *
 * The host already does this on its own once Secret Manager 1.2.17 or newer is installed
 * (`RetiredPlugins`), so this button is for the cases where that pass does not run or declines:
 * a host older than the release that added it, or a machine that never installed Secret Manager.
 * Those are exactly the installs the deliberately low api floor exists to reach.
 *
 * **Delete first, then disable. The order is the whole design.**
 *
 * `disablePlugin` calls `trackingContext.unregisterAll()`, which takes this panel off the sidebar
 * - and the composition it is called from with it. So it has to be the *last* thing: with the
 * order reversed, the coroutine doing the work is cancelled halfway and the jar survives, which
 * is the one outcome that brings the plugin back at the next launch.
 *
 * **There is deliberately no `unloadPlugin` call.** Uninstalling yourself by unloading yourself
 * is a classloader pulling its own foundation out: `PluginRemoval` in the host says it plainly -
 * "deleting a jar out from under a live classloader is how you get `NoClassDefFoundError` from
 * code that is still running". `disablePlugin` gets the same *observable* result (panel gone, and
 * `PluginPersistence.setPluginEnabled` writes `enabled = false`, so the next launch skips the row
 * rather than reporting a missing file) without this code being torn out mid-call. The jar is
 * already deleted by then, so there is nothing left for a re-enable to load.
 *
 * Everything here is api that predates the 1.0.20 floor, and the file work is plain `java.io`. So
 * unlike the Open and Install buttons, this one works on **every** host - which is the point,
 * since the hosts without the automatic pass are the old ones.
 */
internal class SelfUninstall(
    private val loader: PluginLoaderDelegate?,
    private val deleteFile: (File) -> Boolean = { runCatching { it.delete() }.getOrDefault(false) },
    private val exists: (File) -> Boolean = { runCatching { it.isFile }.getOrDefault(false) },
) {
    suspend fun run(): UninstallOutcome {
        val delegate = loader ?: return UninstallOutcome.FAILED

        // The jar this plugin was loaded from, asked of the host rather than guessed from the
        // classloader: the filename convention differs between the store, the wizard and a local
        // build, so the recorded path is the only reliable answer.
        //
        // "Could not ask" is kept apart from "nothing recorded", which is a distinction the first
        // version of this collapsed - and a delegate that threw on every call then reported
        // "removed, restart to finish" while nothing whatsoever had happened.
        val loaded =
            runCatching { delegate.getLoadedPlugins() }.getOrNull() ?: return UninstallOutcome.FAILED
        val jarPath = loaded.firstOrNull { it.pluginId == RetiredPluginVersion.PLUGIN_ID }?.jarPath

        // A blank or absent path IS a legitimate state, and not a failure: disabling alone still
        // stops the plugin loading, and reporting failure for a jar nobody recorded would leave
        // the user pressing a button that says it did not work when it did.
        val artifactsGone = if (jarPath.isNullOrBlank()) true else purge(File(jarPath))

        val disabled =
            runCatching { delegate.disablePlugin(RetiredPluginVersion.PLUGIN_ID) }.getOrDefault(false)

        return when {
            disabled -> UninstallOutcome.REMOVED
            artifactsGone -> UninstallOutcome.REMOVED_RESTART_REQUIRED
            else -> UninstallOutcome.FAILED
        }
    }

    /**
     * Deletes the jar and its signature sidecar, reporting whether the jar is gone afterwards.
     *
     * The sidecar goes too, for the reason the host's own cleanup documents: reinstalling the same
     * version reuses the filename, so a signature left beside it meets fresh bytes and hard-fails
     * that load - worse than being unsigned. Named by convention (`<jar>.sig`) because
     * `PluginSignatureSidecar` is host-side and not on the plugin api.
     *
     * Re-checks existence rather than trusting `delete()`, which returns false both for a file
     * that was already absent and for one a lock refused to remove.
     */
    private fun purge(jar: File): Boolean {
        val sidecar = File(jar.path + SIDECAR_SUFFIX)
        if (exists(sidecar)) deleteFile(sidecar)
        if (exists(jar)) deleteFile(jar)
        return !exists(jar)
    }

    private companion object {
        const val SIDECAR_SUFFIX = ".sig"
    }
}
