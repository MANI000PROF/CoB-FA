package com.cobfa.app.ui.leaderboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cobfa.app.R
import com.cobfa.app.data.remote.FirestoreService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    currentUid: String,
    city: String,
    state: String,
    mode: LeaderboardViewModel.Mode,
    rows: List<FirestoreService.PublicUser>,
    loading: Boolean,
    error: String?,
    onModeChange: (LeaderboardViewModel.Mode) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(mode, city, state) {
        when (mode) {
            LeaderboardViewModel.Mode.CITY -> if (city.isNotBlank() && state.isNotBlank()) onReload()
            LeaderboardViewModel.Mode.STATE -> if (state.isNotBlank()) onReload()
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val youIndex = rows.indexOfFirst { it.uid == currentUid }
    val youRank = if (youIndex >= 0) youIndex + 1 else null
    val youRow = if (youIndex >= 0) rows[youIndex] else null
    val scopeText =
        if (mode == LeaderboardViewModel.Mode.CITY) "City • $city, $state" else "State • $state"

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Leaderboard") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LeaderboardEntrance(index = 0) {
                    LeaderboardHeroCard(
                        scopeText = scopeText,
                        mode = mode
                    )
                }
            }

            item {
                LeaderboardEntrance(index = 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == LeaderboardViewModel.Mode.CITY,
                            onClick = { onModeChange(LeaderboardViewModel.Mode.CITY) },
                            label = { Text("City") }
                        )
                        FilterChip(
                            selected = mode == LeaderboardViewModel.Mode.STATE,
                            onClick = { onModeChange(LeaderboardViewModel.Mode.STATE) },
                            label = { Text("State") }
                        )
                    }
                }
            }

            if (mode == LeaderboardViewModel.Mode.CITY) {
                item {
                    Text(
                        "Tip: Leaderboards use public profile info such as nickname and region.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                LeaderboardEntrance(index = 2) {
                    YouSpotlightCard(
                        mode = mode,
                        youRank = youRank,
                        youRow = youRow
                    )
                }
            }

            when {
                loading -> {
                    item {
                        SectionCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(strokeWidth = 3.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Loading leaderboard…", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Fetching public ranks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                error != null -> {
                    item {
                        SectionCard {
                            Text("Couldn’t load leaderboard", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onReload) { Text("Retry") }
                        }
                    }

                    if (error.contains("index", ignoreCase = true)) {
                        item {
                            Text(
                                "If this is a Firestore index issue, open the Logcat link to create the index and try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                rows.isEmpty() -> {
                    item {
                        SectionCard {
                            Text("No users yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Once multiple users start earning points, rankings will show here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    itemsIndexed(items = rows, key = { _, u -> u.uid }) { idx, u ->
                        LeaderboardEntrance(index = (idx + 3).coerceAtMost(8)) {
                            LeaderboardRowCard(
                                rank = idx + 1,
                                user = u,
                                mode = mode,
                                isYou = u.uid == currentUid
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardHeroCard(
    scopeText: String,
    mode: LeaderboardViewModel.Mode
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.leaderboard_anim)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.07f),
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(88.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Climb the leaderboard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (mode == LeaderboardViewModel.Mode.CITY) {
                        "See how you rank among users in your city."
                    } else {
                        "Track your standing across users in your state."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = scopeText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YouSpotlightCard(
    mode: LeaderboardViewModel.Mode,
    youRank: Int?,
    youRow: FirestoreService.PublicUser?
) {
    SectionCard {
        Text("You", style = MaterialTheme.typography.titleMedium)

        if (youRow == null || youRank == null) {
            Text(
                "Your rank will appear here once loaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LeaderboardRowContent(
                rank = youRank,
                user = youRow,
                mode = mode,
                isYou = true,
                compact = false
            )
        }
    }
}

@Composable
private fun LeaderboardRowCard(
    rank: Int,
    user: FirestoreService.PublicUser,
    mode: LeaderboardViewModel.Mode,
    isYou: Boolean
) {
    val container = when {
        isYou -> MaterialTheme.colorScheme.secondaryContainer
        rank == 1 -> MaterialTheme.colorScheme.tertiaryContainer
        rank == 2 -> MaterialTheme.colorScheme.primaryContainer
        rank == 3 -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val borderColor = when {
        isYou -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        rank <= 3 -> medalTint(rank).copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (rank <= 3 || isYou) 3.dp else 1.dp)
    ) {
        LeaderboardRowContent(
            rank = rank,
            user = user,
            mode = mode,
            isYou = isYou,
            compact = true
        )
    }
}

@Composable
private fun LeaderboardRowContent(
    rank: Int,
    user: FirestoreService.PublicUser,
    mode: LeaderboardViewModel.Mode,
    isYou: Boolean,
    compact: Boolean
) {
    val region =
        if (mode == LeaderboardViewModel.Mode.CITY) "${user.city}, ${user.state}" else user.state

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = if (compact) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank = rank)

        Spacer(modifier = Modifier.width(12.dp))

        UserAvatar(
            name = user.username,
            profilePicUrl = user.profilePicUrl
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f, fill = true)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = user.username.ifBlank { "Unknown user" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isYou) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "You",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = region.ifBlank { "Region unavailable" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.width(88.dp),
            horizontalAlignment = Alignment.End
        ) {
            AssistChip(
                onClick = { },
                enabled = false,
                label = { Text(tier(user.pointsBalance), maxLines = 1) }
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "${user.pointsBalance} pts",
                style = MaterialTheme.typography.labelLarge,
                color = if (rank <= 3) medalTint(rank) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun UserAvatar(
    name: String,
    profilePicUrl: String
) {
    val context = LocalContext.current
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"

    if (profilePicUrl.isNotBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(profilePicUrl)
                .crossfade(true)
                .build(),
            contentDescription = "$name profile picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )
    } else {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val medal = medalFor(rank)
    val tint = medalTint(rank)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (medal != null) {
                Icon(
                    imageVector = medal,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LeaderboardEntrance(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 350,
            delayMillis = index * 70,
            easing = FastOutSlowInEasing
        ),
        label = "leaderboardAlpha$index"
    )

    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 24f,
        animationSpec = tween(
            durationMillis = 420,
            delayMillis = index * 70,
            easing = FastOutSlowInEasing
        ),
        label = "leaderboardTranslation$index"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
        }
    ) {
        content()
    }
}

@Composable
private fun SectionCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

private fun tier(points: Int): String = when {
    points >= 300 -> "Platinum"
    points >= 150 -> "Gold"
    points >= 50 -> "Silver"
    else -> "Bronze"
}

private fun medalFor(rank: Int) = when (rank) {
    1, 2, 3 -> Icons.Default.EmojiEvents
    else -> null
}

@Composable
private fun medalTint(rank: Int) = when (rank) {
    1 -> Color(0xFFFFC107) // gold
    2 -> Color(0xFFB0BEC5) // silver
    3 -> Color(0xFFCD7F32) // bronze
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

