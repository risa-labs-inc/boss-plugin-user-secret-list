# AGENTS.md

## Project Overview

**My Secrets (Dynamic)** (`ai.rever.boss.plugin.dynamic.usersecretlist`) is a **retired** dynamic
plugin for the BOSS desktop application.

Retired - now the "Shared with me" section of Secret Manager

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
- **Keep the panel id, icon and slot.** A saved sidebar layout keys on the panel id, and the
  user has had that Key icon in that slot since their first run. Move it and the notice
  explaining where their secrets went is the thing that disappears.

The host removes this plugin on startup once Secret Manager is installed at or above the version
that has the sections (BossConsole `RetiredPlugins`). The notice covers older hosts, which have
no such pass.

- **Plugin ID**: `ai.rever.boss.plugin.dynamic.usersecretlist`
- **Main Class**: `ai.rever.boss.plugin.dynamic.usersecretlist.UserSecretListDynamicPlugin`
- **API Version**: 1.0.20

## Essential Commands

```bash
./gradlew buildPluginJar    # Build plugin JAR (output: build/libs/)
./gradlew build              # Full build
./gradlew processResources   # Process resources (syncs version)
```

## Workflow Rules

- Do NOT run the BOSS application to test. The user will test manually.
- After building, copy JAR to `~/.boss/plugins/` for local testing.

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
- **Coroutines**: Async operations

## Version Management

**`build.gradle.kts` is the single source of truth for version.**

The `processResources` task automatically syncs the version into `plugin.json` at build time. Never manually edit the version in `plugin.json` - only change it in `build.gradle.kts`.

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
