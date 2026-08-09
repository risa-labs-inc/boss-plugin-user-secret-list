package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.CreateSecretRequestData
import ai.rever.boss.plugin.api.McpToolArgs
import ai.rever.boss.plugin.api.McpToolResult
import ai.rever.boss.plugin.api.PaginatedSecretsData
import ai.rever.boss.plugin.api.PaginatedSecretsWithSharingData
import ai.rever.boss.plugin.api.SecretDataProvider
import ai.rever.boss.plugin.api.SecretEntryWithSharingData
import ai.rever.boss.plugin.api.SecretShareData
import ai.rever.boss.plugin.api.ShareSecretRequestData
import ai.rever.boss.plugin.api.UnshareSecretRequestData
import ai.rever.boss.plugin.api.UpdateSecretRequestData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the `my_secret_get` AI-provider refusal.
 *
 * The refusal exists because this tool and Secret Manager's `secret_get` read the same
 * store under the same `secret.read` gate, so a gate in only one of them is bypassable by
 * calling the other. That is exactly what shipped, and nothing failed - hence these tests.
 */
class UserSecretListMcpToolsTest {

    @Test
    fun `my_secret_get refuses an ai-provider tagged secret`() = runTest {
        val result = getSecret("k1", tags = listOf("ai-provider", "api-key", "anthropic"))

        assertTrue(result.isError, "an ai-provider key must be refused, not returned")
        assertFalse(
            result.text.contains(PROVIDER_KEY),
            "the refusal must not leak the value it is refusing: ${result.text}",
        )
    }

    /**
     * The comparison, not just the constant. An exact `contains` fails OPEN on a stored
     * "AI-Provider" or a trailing space from a hand-edited tag, and nothing signals it.
     */
    @Test
    fun `a differently-cased or padded tag is still refused`() = runTest {
        for (tag in listOf("AI-Provider", "ai-provider ", " Ai-Provider")) {
            val result = getSecret("k$tag", tags = listOf(tag))
            assertTrue(result.isError, "tag <$tag> must still be refused")
            assertFalse(result.text.contains(PROVIDER_KEY), "leaked the key for tag <$tag>")
        }
    }

    /** A store failure must not read as "there is no such secret". */
    @Test
    fun `a store failure is reported as a failure`() = runTest {
        val provider = UserSecretListMcpToolProvider("test", FailingSecrets())
        val tool = provider.tools().first { it.name == "my_secret_get" }

        val result = tool.handler.call(McpToolArgs(mapOf("id" to "k1")))

        assertTrue(result.isError)
        assertContains(result.text, "Failed:", message = "a provider failure must not look like a miss")
    }

    /**
     * The path this plugin exists for. Every other case here builds an OWNED entry, but the
     * refusal reads `tags` off the sharing DTO, so an owner-only suite would stay green if
     * the shared projection ever dropped them.
     *
     * The server side is uniform - `get_user_secrets_with_shared` unions the five access
     * sources carrying only ids, then joins back to `secrets` and computes tags once, so a
     * shared row and an owned row get identical tags. This pins the plugin's half of that.
     */
    @Test
    fun `an ai-provider key shared with me is refused too`() = runTest {
        val result = getSecret("k3", tags = listOf("ai-provider"), isOwner = false, accessLevel = "read")

        assertTrue(result.isError, "a shared provider key must be refused like an owned one")
        assertFalse(result.text.contains(PROVIDER_KEY))
    }

    @Test
    fun `my_secret_get still reveals an ordinary secret`() = runTest {
        val result = getSecret("k2", tags = listOf("api-key"))

        assertFalse(result.isError, "an untagged secret must still be readable: ${result.text}")
        assertContains(result.text, PROVIDER_KEY)
    }

    /**
     * Pins the literal against a careless local edit, and nothing more.
     *
     * It deliberately does NOT promise the cross-repo guarantee its old name implied: no
     * code here reads `ProviderCredentialStore.TAG_AI_PROVIDER`, so this cannot catch drift
     * on Secret Manager's side. The value is part of the on-record data, so it cannot move
     * without breaking that plugin's own reads either - but the durable fix is hoisting the
     * constant onto `boss-plugin-api` so both plugins import one definition.
     */
    @Test
    fun `the refused tag literal has not been edited`() {
        assertEquals("ai-provider", UserSecretListMcpToolProvider.TAG_AI_PROVIDER)
    }

    @Test
    fun `both tools require secret read`() {
        val tools = UserSecretListMcpToolProvider("test", FakeSecrets(emptyList())).tools()

        assertEquals(setOf("my_secrets_list", "my_secret_get"), tools.map { it.name }.toSet())
        tools.forEach {
            assertEquals(listOf("secret.read"), it.requiredPermissions, "${it.name} lost its gate")
        }
    }

    // ---------------------------------------------------------------------

    private suspend fun getSecret(
        id: String,
        tags: List<String>,
        isOwner: Boolean = true,
        accessLevel: String = "owner",
    ): McpToolResult {
        val provider =
            UserSecretListMcpToolProvider("test", FakeSecrets(listOf(entry(id, tags, isOwner, accessLevel))))
        val tool = provider.tools().first { it.name == "my_secret_get" }
        return tool.handler.call(McpToolArgs(mapOf("id" to id)))
    }

    private fun entry(
        id: String,
        tags: List<String>,
        isOwner: Boolean = true,
        accessLevel: String = "owner",
    ) = SecretEntryWithSharingData(
        id = id,
        website = "api.anthropic.com",
        username = "default",
        password = PROVIDER_KEY,
        notes = null,
        tags = tags,
        createdAt = "2026-08-09T00:00:00Z",
        updatedAt = "2026-08-09T00:00:00Z",
        isOwner = isOwner,
        accessLevel = accessLevel,
    )

    private companion object {
        const val PROVIDER_KEY = "sk-ant-test-not-a-real-key"
    }

    /** Fails the one read `my_secret_get` performs. */
    private class FailingSecrets : SecretDataProvider by FakeSecrets(emptyList()) {
        override suspend fun getUserSecretsWithSharingInfo(
            limit: Int,
            offset: Int,
        ): Result<PaginatedSecretsWithSharingData> = Result.failure(IllegalStateException("not signed in"))
    }

    /** Only [getUserSecretsWithSharingInfo] is exercised; the rest of the interface is unused here. */
    private class FakeSecrets(private val entries: List<SecretEntryWithSharingData>) : SecretDataProvider {
        override suspend fun getUserSecretsWithSharingInfo(
            limit: Int,
            offset: Int,
        ): Result<PaginatedSecretsWithSharingData> =
            Result.success(PaginatedSecretsWithSharingData(entries, hasMore = false))

        override suspend fun getUserSecrets(limit: Int, offset: Int): Result<PaginatedSecretsData> = unsupported()

        override suspend fun searchSecrets(query: String, limit: Int, offset: Int): Result<PaginatedSecretsData> =
            unsupported()

        override suspend fun createSecret(request: CreateSecretRequestData): Result<Unit> = unsupported()

        override suspend fun updateSecret(request: UpdateSecretRequestData): Result<Unit> = unsupported()

        override suspend fun deleteSecret(id: String): Result<Unit> = unsupported()

        override suspend fun getSecretShares(secretId: String): Result<List<SecretShareData>> = unsupported()

        override suspend fun shareSecret(request: ShareSecretRequestData): Result<Unit> = unsupported()

        override suspend fun unshareSecret(request: UnshareSecretRequestData): Result<Unit> = unsupported()

        private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException("not used"))
    }
}
