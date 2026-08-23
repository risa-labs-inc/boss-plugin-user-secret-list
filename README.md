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

One panel, in the same slot with the same icon, saying where the list went. That is all:

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
./gradlew test    # 2 cases: the dependency declaration and the version floors
```

## License

Proprietary - Risa Labs Inc.
