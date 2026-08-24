# BOSS My Secrets (retired)

**This plugin is retired.** Its list is now the "Shared with me" section of
[Secret Manager](https://github.com/risa-labs-inc/boss-plugin-secret-manager). Install that
instead; there is nothing here to install for its own sake.

## Why it went away

Two panels sat next to each other in the right sidebar reading the same vault:

| | Secret Manager | My Secrets (this plugin) |
|---|---|---|
| Reads | `getUserSecrets` (own + organisation) | `getUserSecretsWithSharingInfo` (own + organisation + shared) |
| Does | full CRUD, sharing, Store API keys, AI provider settings | read-only list with badges |

**Both listed the caller's own secrets**, which is what made two panels confusing rather than
complementary. Secret Manager now has two sections that partition the vault instead of
overlapping it, split on how each secret reached you: what you can manage, and what someone
shared with you.

This plugin was also the one the first-run wizard installed by default while Secret Manager was
not, so a typical install had the read-only half and not the half that can add an API key, even
though every AI provider setting lives in Secret Manager.

## What this version still does

One panel, in the same slot with the same icon, saying where the list went - with a button for
getting to Secret Manager, and one for removing this panel entirely:

| Secret Manager | The notice offers |
|---|---|
| installed and enabled | **Open Secret Manager** |
| absent, disabled, unhealthy or incompatible | **Install Secret Manager** - the Toolbox asks you to confirm, then installs |
| either, on a host older than api 1.0.57 | no button - text saying where to look |

**No plugin-facing api installs another plugin**, and none dispatches a deep link either - so
Install hands a `boss://plugin?…&action=install&plugin=…` URL to the OS, which owns that scheme
and routes it back into this instance, to the Toolbox's own deep-link handler. The Toolbox then
shows a confirm dialog naming the plugin *from the store* and installs on the answer. That handler
exists so a web page can offer Install without being trusted about what is installed, which makes
it the right door for a plugin that cannot be trusted about it either.

It needs Toolbox **1.9.14** or newer (the release that added the handler); on an older one the
button falls back to opening the Toolbox, and says so.

"Installed" is read from `getLoadedPlugins()` and requires `isEnabled`, `healthy` and
`!isIncompatible` - not `isPluginLoaded`, which was the first version and is wrong in a way that
shows: `disablePlugin` flips the state without unloading, so a plugin the user switched off is
still "loaded" while its panel is not registered. Offering Open there is a button that does
nothing.

The third row is the reason for the capability probe. `PanelEventProvider.openPanel` arrived in
api **1.0.57** and this plugin's floor is deliberately **1.0.20**, so on a real share of the
installs this release is built to reach, a button would be dead. The probe is reflective rather
than a trial call, because calling `openPanel` to find out whether it exists reveals a panel -
and it is answered while the plugin registers, so the probe itself would pop the notice open.

**Uninstall** removes this plugin from inside itself, for the hosts the automatic pass never
reaches: one older than the release that added it, or a machine that never installed Secret
Manager. It deletes the jar and its signature sidecar, then disables the plugin - in that order,
because disabling unregisters this panel and would cancel the work halfway, leaving the jar to
load again at the next launch. There is deliberately no `unloadPlugin` call: uninstalling yourself
by unloading yourself is a classloader pulling out its own foundation. Two-step confirmation, and
you can install it again from the Plugin Store.

Unlike Open and Install, Uninstall needs nothing newer than the api floor - which is the point,
since the hosts without the automatic pass are the old ones.

That is all it does otherwise:

- **No secrets are read.** The component holds no `SecretDataProvider` and no list.
- **No MCP tools.** `my_secrets_list` and `my_secret_get` moved to Secret Manager under the
  same names, so nothing that calls them breaks. Leaving a second copy here is the exact shape
  of the bug that once let `my_secret_get` read the AI provider keys `secret_get` withholds:
  same vault, same `secret.read` gate, two implementations, one of them missing the check.
  There is now one gate and one implementation of it.
- **Secret Manager is declared as a dependency**, so a user who has this plugin and not that
  one gets the host's install prompt when this version lands, rather than a dead sidebar icon.
  Dependencies are not enforced at load time, so the notice still renders if they decline.

A recent BOSS uninstalls this plugin for the user once Secret Manager is installed. On an older
host it stays until removed by hand from the Toolbox, which is why the notice is a panel and not
an absence.

## Requirements

Unchanged on purpose: BOSS >= 9.2.20, boss-plugin-api >= 1.0.20. This release has to reach every
host that still shows the old panel, and raising either floor would make the updater skip exactly
those installs. `RetirementManifestTest` pins both, and the dependency declaration with them.

## Build

```bash
./gradlew buildPluginJar
./gradlew test    # 41 cases: the manifest facts, the notice's buttons, the self-uninstall
```

## License

Proprietary - Risa Labs Inc.
