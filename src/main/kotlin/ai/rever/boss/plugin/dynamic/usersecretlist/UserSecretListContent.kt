package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.SecretDataProvider
import ai.rever.boss.plugin.api.SecretEntryWithSharingData
import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.ui.BossTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Theme colors
private val BossDarkBackground = Color(0xFF2B2D30)
private val BossDarkSurface = Color(0xFF3C3F41)
private val BossDarkBorder = Color(0xFF4E5254)
private val BossDarkTextSecondary = Color(0xFF9E9E9E)
private val BossGreen = Color(0xFF4CAF50)
private val BossBlue = Color(0xFF64B5F6)
private val BossOrange = Color(0xFFFFB74D)

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
                    .background(BossDarkBackground)
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
                                tint = BossGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                "My Secrets",
                                color = Color.White,
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
                                tint = Color.White
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
                        color = Color.Gray,
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
            .background(BossDarkBackground, RoundedCornerShape(6.dp))
            .border(1.dp, BossDarkBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.body2.copy(color = Color.White),
        cursorBrush = SolidColor(BossGreen),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = BossDarkTextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search by website or username...",
                            color = BossDarkTextSecondary,
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
                        color = BossGreen,
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
                    color = Color.Gray,
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = BossDarkSurface,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Website, Username, and Ownership Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Website with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "Website",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = secret.website,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Username with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Username",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = secret.username,
                            color = Color.Gray,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Ownership badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (secret.isOwner) BossGreen else BossBlue,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (secret.isOwner) Icons.Default.Person else Icons.Default.Share,
                            contentDescription = if (secret.isOwner) "Owner" else "Shared",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (secret.isOwner) "Owner" else "Shared",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Divider(color = BossDarkBorder, thickness = 1.dp)

            // Action button: Copy Username only
            Button(
                onClick = {
                    scope.launch {
                        clipboardManager.setText(AnnotatedString(secret.username))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = BossDarkBackground,
                    contentColor = BossBlue
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

            // Shared by information (only for shared secrets)
            if (!secret.isOwner && secret.sharedByEmail != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BossDarkBackground, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = "Shared by",
                        tint = BossBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Shared by: ${secret.sharedByEmail}",
                        color = Color.Gray,
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
                        color = BossGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        if (isMetadataExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isMetadataExpanded) "Hide" else "Show",
                        tint = BossGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Expanded metadata section
                if (isMetadataExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BossDarkBackground, RoundedCornerShape(4.dp))
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
                                    tint = BossGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    secret.tags.joinToString(", "),
                                    color = Color.White,
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
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                )
                                Text(
                                    notes,
                                    color = Color.Gray,
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
                                    tint = BossOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "Expires: $expirationDate",
                                    color = BossOrange,
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
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Created: ${secret.createdAt}",
                                color = Color.Gray,
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
            CircularProgressIndicator(color = BossGreen)
            Text(
                "Loading your secrets...",
                color = Color.Gray,
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
                color = Color(0xFFE57373),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                message,
                color = Color.Gray,
                fontSize = 14.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossGreen
                    )
                ) {
                    Text("Retry", color = Color.White)
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = Color.Gray)
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
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    "No secrets found",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "You don't have any secrets yet, or none have been shared with you.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "No results",
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    "No results found",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Try a different search term",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}
