# AGENTS.md

## Project Overview

**My Secrets (Dynamic)** (`ai.rever.boss.plugin.dynamic.usersecretlist`) is a **retired** dynamic
plugin for the BOSS desktop application.

Retired - now the "Shared with me" section of Secret Manager

- **Plugin ID**: `ai.rever.boss.plugin.dynamic.usersecretlist`
- **Main Class**: `ai.rever.boss.plugin.dynamic.usersecretlist.UserSecretListDynamicPlugin`
- **API Version**: 1.0.20

## Retired: do not add features here

The list this plugin served is now a section of
[secret-manager](https://github.com/risa-labs-inc/boss-plugin-secret-manager). Two panels read
the same vault and both listed the caller's own secrets; Secret Manager now has two sections
split on how each secret reached you.

What is left is a pointer panel (`MovedNotice`) in the same slot with the same icon, plus a
declared dependency on Secret Manager so the host offers to install it. Three things to keep if
you touch this repo at all:

- **No MCP tools.** `my_secrets_list` / `my_secret_get` moved to Secret Manager under the same
  names. A second copy of `my_secret_get` here is how it once shipped without the
  `ai-provider` refusal `secret_get` carries and read exactly the keys that gate withholds.
  One gate, one implementation.
- **Do not raise `minBossVersion` or `apiVersion`.** This release has to reach every host that
  still shows the old panel; a raised floor makes the updater skip precisely those installs.
  `RetirementManifestTest` pins both.
- **`"optional": false` is safe, and it was checked rather than assumed.** The pointer panel only
  renders if a missing required dependency does not block *loading* - on the oldest host in the
  range, not just current main. Verified against the tags: at `v9.2.20` the only live read of
  `manifest.dependencies` is `checkCanUnload` (refusing to unload a dependency, not to load a
  dependent); `v9.3.20` has none in composeApp at all; `v9.4.15` and `v9.4.30` have
  `PluginDependencyResolution` and `MissingDependencyReporter`, both of which *report*.
  `PluginDependencyResolver` was never constructed anywhere in the repo's history. If that ever
  changes, `optional: true` is the safer trade - the install prompt is a nice-to-have, the notice
  rendering is the whole point.
- **Keep the panel id, icon and slot.** A saved sidebar layout keys on the panel id, and the
  user has had that Key icon in that slot since their first run. Move it and the notice
  explaining where their secrets went is the thing that disappears. Pinned, along with the
  manifest's `panel.priority` agreeing with `PanelId`'s order - two independent copies of one
  fact that nothing else would notice drifting.

The host removes this plugin on startup once Secret Manager is installed at or above the version
that has the sections (BossConsole `RetiredPlugins`). The notice covers older hosts, which have
no such pass.

### The notice's button

`SecretManagerLink` holds the whole feature, and everything newer than the 1.0.20 floor is
confined to it. `movedNoticeAction` is the decision, as a pure function, because it is the one
thing here worth testing and the plugin itself cannot be instantiated in a test.

Three things that are easy to get wrong, all pinned:

- **"Installed" is not `isPluginLoaded`.** `DynamicPluginManager.disablePlugin` flips the state
  to DISABLED and never calls `pluginLoader.unloadPlugin`, so a plugin the user switched off is
  still in `getLoadedPlugins()` - and its panel is not registered, so Open would be a button that
  does nothing. The check requires `isEnabled`, `healthy` and `!isIncompatible`.
- **The capability probe is reflective, not a trial call.** `openPanel` arrived in api 1.0.57 and
  the floor is 1.0.20, so its absence has to be detected rather than discovered under a press.
  Calling it to find out reveals a panel, and the answer is needed at registration - so the probe
  would pop the notice open by itself. It asks the host's *implementation* class for the method,
  because the api's default no-op body means the interface always has it.
- **There is no install call, and the button says so.** No plugin-facing api installs another
  plugin. "Install" opens the Toolbox, with a supporting line saying that is what it does.

The `methodNamesOf` seam exists because the probe is otherwise untestable: `openPanel` has a
default body, and Kotlin synthesises an override into every implementing class, so a hand-written
fake that does *not* override it still reports the method and the "old host" case is unreachable.

`stateHolderClass` is gone from the manifest. It named *secret-manager's* host-side state holder
for a panel that holds no state; only `PluginProcessMain` reads it, under `BOSS_MODE=KERNEL`,
guarded by `isNotEmpty()` inside a try/catch - so both its absence and a missing class were
already safe, and the honest value is none.

## Essential Commands

```bash
./gradlew buildPluginJar    # Build plugin JAR (output: build/libs/)
./gradlew build              # Full build
./gradlew processResources   # Process resources (syncs version)
```

## Workflow Rules

- Do NOT run the BOSS application to test. The user will test manually.
- There is nothing here to test by hand any more: the panel is a static notice. Build it to
  check it compiles, and let `RetirementManifestTest` cover the manifest.

## Architecture

### Plugin Structure
```
src/main/kotlin/   → Plugin source code (package: ai.rever.boss.plugin.dynamic.*)
src/main/resources/META-INF/boss-plugin/plugin.json → Plugin manifest
build.gradle.kts   → Build config + version (single source of truth)
```

### Key Patterns
- Entry point: `DynamicPlugin` interface with `register(context)` and `dispose()`
- UI: `PanelComponentWithUI` with `@Composable Content()`
- There is no ViewModel and no provider access any more - see "Retired" above. The whole
  plugin is `UserSecretListDynamicPlugin` -> `UserSecretListComponent` -> `MovedNotice`.

### Dependencies
- **boss-plugin-api**: compileOnly (provided by host app at runtime)
- **Compose Desktop**: UI framework
- **Decompose**: Navigation and component lifecycle

Coroutines went with the ViewModel - nothing here suspends. So did the slf4j test backend, which
existed because `BossLogger` binds at class-init and every deleted class held a logger.

## Version Management

**`build.gradle.kts` is the single source of truth for version.**

The `processResources` task automatically syncs the version into `plugin.json` at build time. Never manually edit the version in `plugin.json` - only change it in `build.gradle.kts`.

`DynamicPlugin.version` reads that stamped manifest through `RetiredPluginVersion` rather than
carrying a literal, which had already drifted to `1.0.5` against a manifest saying `1.2.5`.

**It selects its own manifest by `mainClass`, and neither the id nor the `pluginId` field would
do.** Every plugin ships `plugin.json` at the same path and lookup is parent-first, so the
document has to be identified rather than assumed. A bare `contains("<our id>")` matches any
manifest that merely *mentions* the id - and this release created that situation, by declaring a
dependency on secret-manager. `"pluginId": "<our id>"` is no better: **a dependency entry uses
that same key**, so the obvious one-line fix for the substring bug still had the bug. `mainClass`
names a type that exists only in this jar and cannot appear inside a dependency entry, and
`the manifest names the class the host will load` keeps the constant and the manifest in step.

The selection is a separate `internal` function because testing the *pattern* proved nothing:
with only our own manifest on the test classpath, a reader matching a bare substring passed
either way. The test feeds `selectOwnManifest` an imposter first, which is the parent-first
ordering that actually breaks it.

**Two traps that stamping creates, both now pinned by tests:**

- **`processResources` is line-based and rewrites every `"version": "..."` line in the file.**
  A `"version": "*"` inside the `dependencies` block therefore came out of the built jar as
  `"version": "1.2.5"` - "requires secret-manager 1.2.5", using *this* plugin's number. The
  field defaults to `"*"` and the host ignores it, so it is simply absent now. This was invisible
  in the committed file and only showed up on reading the jar.
- **Asserting the reported version against the bundled manifest is circular** - both read the
  same file, so any value in it passes. The Test task injects
  `boss.plugin.expectedVersion` from Gradle instead, which also catches a stamp that did not
  happen.

## Code Quality

- Use Compose Multiplatform APIs (not Android-specific)
- All Kotlin files must end with a newline
- Handle null providers gracefully - show fallback UI, never crash

## CI/CD

Pushes to `main` trigger the release workflow which:
1. Builds the plugin JAR
2. Creates a GitHub release
3. Publishes to the BOSS Plugin Store

The workflow is defined in `.github/workflows/build.yml` and delegates to the shared workflow in `risa-labs-inc/BossConsole-Releases`.
