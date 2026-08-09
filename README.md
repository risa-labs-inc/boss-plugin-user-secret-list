# BOSS My Secrets

A read-only view of the secrets you own and the ones shared with you, in the right sidebar.

The consumer-side counterpart to [Secret
Manager](https://github.com/risa-labs-inc/boss-plugin-secret-manager). Both read the same
`SecretDataProvider`, but this panel adds the sharing dimension Secret Manager's list does not
show, and deliberately offers no management controls at all.

## What it does

- **Lists secrets you own plus secrets shared with you**, through
  `getUserSecretsWithSharingInfo`.
- **Owner and Shared badges**, with the access level for shared entries.
- **Filter** by website or username, applied client-side over what is loaded.
- **Copy** a website or username to the clipboard.
- **Expand an entry** for its metadata: tags, notes, expiration, and who shared it.
- **Paged at 50** with a load-more control, and it surfaces how long the last load took.

Passwords are masked in the list. There are no edit, delete or share actions anywhere in this
panel by design - use Secret Manager for those.

## MCP tools

| Tool | Purpose |
|---|---|
| `my_secrets_list` | Your own and shared-with-you secrets, as metadata |
| `my_secret_get` | Reveal the password and notes for one secret id |

Both are gated on the `secret.read` permission.

**`my_secret_get` refuses any secret tagged `ai-provider`**, matching Secret Manager's
`secret_get`. The tag is compared case-insensitively and trimmed, because an exact match
fails *open* on a hand-edited tag and nothing would signal it. Both plugins read the same store under the same gate, so a refusal in only one
of them is not a refusal - the agent would just call the other tool. An agent that needs to
*use* a provider goes through `PluginContext.llmProvider` and never needs the raw value.

## Requirements

- BOSS >= 9.2.20, boss-plugin-api >= 1.0.20
- `secretDataProvider`. Without it the plugin registers a stub panel and contributes no MCP
  tools.
- No external binaries.

The panel itself is currently open to any authenticated user; only the MCP tools carry a
permission gate.

## Build

```bash
./gradlew buildPluginJar
cp build/libs/boss-plugin-user-secret-list-*.jar ~/.boss/plugins/
```

See [AGENTS.md](AGENTS.md) for architecture and conventions.

## License

Proprietary - Risa Labs Inc.
