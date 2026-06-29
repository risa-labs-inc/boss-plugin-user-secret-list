package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.SecretDataProvider
import ai.rever.boss.plugin.api.SecretEntryWithSharingData
import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun UserSecretListContent(
    secretDataProvider: SecretDataProvider?,
    scope: CoroutineScope
) {
    val viewModel = remember { UserSecretListViewModel(secretDataProvider, scope) }
    val state by viewModel.state.collectAsState()

    BossTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BossThemeColors.SurfaceColor)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header with title and refresh button
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VpnKey,
                                contentDescription = "My Secrets",
                                tint = BossThemeColors.SuccessColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                "My Secrets",
                                color = BossThemeColors.TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Refresh button
                        IconButton(
                            onClick = { viewModel.loadSecrets() },
                            enabled = !state.isLoading
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = BossThemeColors.TextPrimary
                            )
                        }
                    }

                    // Search bar
                    UserSecretSearchBar(
                        query = state.searchQuery,
                        onQueryChange = { viewModel.searchSecrets(it) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    // Secret count
                    Text(
                        if (state.searchQuery.isBlank()) {
                            "${state.secrets.size} secret${if (state.secrets.size != 1) "s" else ""}"
                        } else {
                            "${state.secrets.size} result${if (state.secrets.size != 1) "s" else ""} for '${state.searchQuery}'"
                        },
                        color = BossThemeColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Content based on state
                    when {
                        state.isLoading -> {
                            UserSecretLoadingView()
                        }
                        state.errorMessage != null -> {
                            UserSecretErrorView(
                                message = state.errorMessage!!,
                                onRetry = { viewModel.loadSecrets() },
                                onDismiss = { viewModel.clearError() }
                            )
                        }
                        state.secrets.isEmpty() -> {
                            UserSecretEmptyView(searchQuery = state.searchQuery)
                        }
                        else -> {
                            UserSecretList(
                                secrets = state.secrets,
                                expandedSecretIds = state.expandedSecretIds,
                                onToggleMetadata = { viewModel.toggleMetadataExpanded(it) },
                                onLoadMore = { viewModel.loadMoreSecrets() },
                                isLoadingMore = state.isLoadingMore,
                                hasMore = state.hasMore,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search bar composable
 */
@Composable
private fun UserSecretSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .height(36.dp)
            .background(BossThemeColors.SurfaceColor, RoundedCornerShape(6.dp))
            .border(1.dp, BossThemeColors.BorderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.body2.copy(color = BossThemeColors.TextPrimary),
        cursorBrush = SolidColor(BossThemeColors.SuccessColor),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = BossThemeColors.TextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search by website or username...",
                            color = BossThemeColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

/**
 * Scrollable list view for user secrets with pagination
 */
@Composable
private fun UserSecretList(
    secrets: List<SecretEntryWithSharingData>,
    expandedSecretIds: Set<String>,
    onToggleMetadata: (String) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Trigger load more when scrolled to bottom
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null &&
                    lastVisibleIndex >= secrets.size - 3 &&
                    hasMore &&
                    !isLoadingMore
                ) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .lazyListScrollbar(
                listState = listState,
                direction = Orientation.Vertical,
                config = getPanelScrollbarConfig()
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(
            items = secrets,
            key = { it.id }
        ) { secret ->
            UserSecretCard(
                secret = secret,
                isMetadataExpanded = expandedSecretIds.contains(secret.id),
                onToggleMetadata = { onToggleMetadata(secret.id) }
            )
        }

        // Loading more indicator
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = BossThemeColors.SuccessColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // End of list indicator
        if (!hasMore && secrets.isNotEmpty()) {
            item {
                Text(
                    "- End of list -",
                    color = BossThemeColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

/**
 * Read-only secret card view
 */
@Composable
private fun UserSecretCard(
    secret: SecretEntryWithSharingData,
    isMetadataExpanded: Boolean,
    onToggleMetadata: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isApiKey = secret.tags.contains("api_key")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = BossThemeColors.SurfaceColor,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Website/Service, Username/Key Name, and Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Website/Service with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (isApiKey) Icons.Default.Api else Icons.Default.Language,
                            contentDescription = if (isApiKey) "Service" else "Website",
                            tint = if (isApiKey) BossThemeColors.WarningColor else BossThemeColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = secret.website,
                            color = BossThemeColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Username/Key Name with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (isApiKey) Icons.Default.Key else Icons.Default.Person,
                            contentDescription = if (isApiKey) "Key Name" else "Username",
                            tint = BossThemeColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = secret.username,
                            color = BossThemeColors.TextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Badges column
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    // API Key badge (if applicable)
                    if (isApiKey) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BossThemeColors.WarningColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = "API Key",
                                    tint = BossThemeColors.TextPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "API Key",
                                    color = BossThemeColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Ownership badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (secret.isOwner) BossThemeColors.SuccessColor else BossThemeColors.AccentColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (secret.isOwner) Icons.Default.Person else Icons.Default.Share,
                                contentDescription = if (secret.isOwner) "Owner" else "Shared",
                                tint = BossThemeColors.TextPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (secret.isOwner) "Owner" else "Shared",
                                color = BossThemeColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Divider(color = BossThemeColors.BorderColor, thickness = 1.dp)

            // Action buttons
            if (isApiKey) {
                // For API Keys: Copy API Key button (primary) and Copy Key Name button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                clipboardManager.setText(AnnotatedString(secret.password))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = BossThemeColors.WarningColor,
                            contentColor = BossThemeColors.TextPrimary
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "Copy API Key",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Copy API Key", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                clipboardManager.setText(AnnotatedString(secret.username))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = BossThemeColors.SurfaceColor,
                            contentColor = BossThemeColors.AccentColor
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Key Name",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Copy Name", fontSize = 12.sp)
                    }
                }
            } else {
                // For regular secrets: Copy Username only
                Button(
                    onClick = {
                        scope.launch {
                            clipboardManager.setText(AnnotatedString(secret.username))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossThemeColors.SurfaceColor,
                        contentColor = BossThemeColors.AccentColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Username",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Copy Username", fontSize = 12.sp)
                }
            }

            // Shared by information (only for shared secrets)
            if (!secret.isOwner && secret.sharedByEmail != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BossThemeColors.SurfaceColor, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = "Shared by",
                        tint = BossThemeColors.AccentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Shared by: ${secret.sharedByEmail}",
                        color = BossThemeColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Show details button (if has tags or notes)
            if (secret.tags.isNotEmpty() || secret.notes != null || secret.expirationDate != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleMetadata() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isMetadataExpanded) "Hide Details" else "Show Details",
                        color = BossThemeColors.SuccessColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        if (isMetadataExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isMetadataExpanded) "Hide" else "Show",
                        tint = BossThemeColors.SuccessColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Expanded metadata section
                if (isMetadataExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BossThemeColors.SurfaceColor, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tags
                        if (secret.tags.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Label,
                                    contentDescription = "Tags",
                                    tint = BossThemeColors.SuccessColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    secret.tags.joinToString(", "),
                                    color = BossThemeColors.TextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Notes
                        val notes = secret.notes
                        if (notes != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Notes,
                                    contentDescription = "Notes",
                                    tint = BossThemeColors.TextSecondary,
                                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                )
                                Text(
                                    notes,
                                    color = BossThemeColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Expiration date
                        val expirationDate = secret.expirationDate
                        if (expirationDate != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Event,
                                    contentDescription = "Expires",
                                    tint = BossThemeColors.WarningColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "Expires: $expirationDate",
                                    color = BossThemeColors.WarningColor,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Created date
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = "Created",
                                tint = BossThemeColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Created: ${secret.createdAt}",
                                color = BossThemeColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Loading view
 */
@Composable
private fun UserSecretLoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = BossThemeColors.SuccessColor)
            Text(
                "Loading your secrets...",
                color = BossThemeColors.TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Error view
 */
@Composable
private fun UserSecretErrorView(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "Error",
                color = BossThemeColors.ErrorColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                message,
                color = BossThemeColors.TextSecondary,
                fontSize = 14.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossThemeColors.SuccessColor
                    )
                ) {
                    Text("Retry", color = BossThemeColors.TextPrimary)
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = BossThemeColors.TextSecondary)
                }
            }
        }
    }
}

/**
 * Empty view (no secrets)
 */
@Composable
private fun UserSecretEmptyView(searchQuery: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            if (searchQuery.isBlank()) {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = "No secrets",
                    tint = BossThemeColors.TextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    "No secrets found",
                    color = BossThemeColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "You don't have any secrets yet, or none have been shared with you.",
                    color = BossThemeColors.TextSecondary,
                    fontSize = 14.sp
                )
            } else {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "No results",
                    tint = BossThemeColors.TextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    "No results found",
                    color = BossThemeColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Try a different search term",
                    color = BossThemeColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}
