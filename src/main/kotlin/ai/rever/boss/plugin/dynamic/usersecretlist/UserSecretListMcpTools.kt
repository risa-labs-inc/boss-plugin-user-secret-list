package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult
import ai.rever.boss.plugin.api.SecretDataProvider

/**
 * MCP tools contributed by the My Secrets plugin: list the current user's own
 * and shared secrets, and reveal one by id.
 *
 * SECURITY: `my_secret_get` reveals secret values to the calling agent; it
 * exists only while this plugin is active for the signed-in user. Registered in
 * [UserSecretListDynamicPlugin.register]; removed automatically on disable/unload.
 *
 * It withholds AI provider keys for the same reason Secret Manager's `secret_get`
 * does - see [TAG_AI_PROVIDER]. The two plugins read the same [SecretDataProvider]
 * and carry the same `secret.read` gate, so a refusal present in only one of them
 * is not a refusal at all: the agent just calls the sibling tool.
 */
internal class UserSecretListMcpToolProvider(
    override val providerId: String,
    private val secrets: SecretDataProvider,
) : McpToolProvider {

    override fun tools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "my_secrets_list",
            description = "List your secrets and secrets shared with you (id, website, username, owner, access).",
            inputSchema = LIMIT_SCHEMA,
            handler = McpToolHandler { args ->
                val limit = (args.int("limit") ?: 100).coerceIn(1, 500)
                secrets.getUserSecretsWithSharingInfo(limit).fold(
                    onSuccess = { page ->
                        if (page.data.isEmpty()) McpToolResult("No secrets.")
                        else McpToolResult(page.data.joinToString("\n") { s ->
                            val owner = if (s.isOwner) "owner" else "shared(${s.accessLevel})"
                            "${s.id}\t${s.website}\t${s.username}\t[$owner]"
                        })
                    },
                    onFailure = { McpToolResult("Failed: ${it.message}", isError = true) },
                )
            },
        ),
        McpToolDefinition(
            name = "my_secret_get",
            description = "Reveal one of your secrets' full value (password, notes) by id. Sensitive.",
            inputSchema = """{"type":"object","properties":{"id":{"type":"string","description":"Secret id from my_secrets_list."}},"required":["id"]}""",
            handler = McpToolHandler { args ->
                val id = args.string("id")
                    ?: return@McpToolHandler McpToolResult("Missing required argument: id", isError = true)
                val entry = secrets.getUserSecretsWithSharingInfo(limit = 500).getOrNull()
                    ?.data?.firstOrNull { it.id == id }
                    ?: return@McpToolHandler McpToolResult("No secret with id $id", isError = true)
                // my_secrets_list hands out ids and this hands out the plaintext password,
                // so without the gate a prompt-injected agent is two tool calls from every
                // configured provider key. An agent that needs to *use* a provider goes
                // through PluginContext.llmProvider and never needs the raw value.
                if (entry.tags.contains(TAG_AI_PROVIDER)) {
                    return@McpToolHandler McpToolResult(
                        "Secret $id is an AI provider key and is not readable through this tool. " +
                            "Use the provider via Settings > AI Providers instead.",
                        isError = true,
                    )
                }
                McpToolResult(
                    buildString {
                        appendLine("website: ${entry.website}")
                        appendLine("username: ${entry.username}")
                        appendLine("password: ${entry.password}")
                        entry.notes?.let { appendLine("notes: $it") }
                        append(if (entry.isOwner) "access: owner" else "access: shared(${entry.accessLevel})")
                    }
                )
            },
        ),
    ).onEach { it.requiredPermissions = listOf("secret.read") }

    internal companion object {
        /**
         * The tag Secret Manager writes onto every stored AI provider credential
         * (`ProviderCredentialStore.TAG_AI_PROVIDER`). Duplicated as a literal rather than
         * imported: the two plugins are separate jars in separate classloaders with no
         * shared module, and the value is part of the on-record data, so it cannot drift
         * without also breaking Secret Manager's own reads.
         */
        const val TAG_AI_PROVIDER: String = "ai-provider"

        const val LIMIT_SCHEMA =
            """{"type":"object","properties":{"limit":{"type":"integer","description":"Max secrets (default 100)."}}}"""
    }
}
