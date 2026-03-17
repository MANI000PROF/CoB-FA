package com.cobfa.app.launch

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cobfa.app.R
import com.cobfa.app.auth.session.DeviceId
import com.cobfa.app.auth.session.SessionWriter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.text.BreakIterator
import java.text.StringCharacterIterator
import kotlin.coroutines.resume

@Composable
fun LaunchScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current

    var contentVisible by remember { mutableStateOf(false) }
    var animatedTitle by remember { mutableStateOf("") }
    var launchProgress by remember { mutableFloatStateOf(0f) }

    val fullTitle = "Cognitive Behavioral Financial Advisor"

    val animatedProgress by animateFloatAsState(
        targetValue = launchProgress,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "launchProgress"
    )

    val logoScale = remember { Animatable(0.88f) }
    val logoAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "launchMotion")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    suspend fun writeSessionAwait(uid: String, deviceId: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            SessionWriter.write(uid, deviceId) { ok ->
                if (continuation.isActive) continuation.resume(ok)
            }
        }

    LaunchedEffect(Unit) {
        contentVisible = true
        launchProgress = 0.08f

        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(550)
            )
        }

        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(700, easing = FastOutSlowInEasing)
            )
        }

        delay(220)

        val breakIterator = BreakIterator.getCharacterInstance()
        breakIterator.text = StringCharacterIterator(fullTitle)
        var nextIndex = breakIterator.next()

        while (nextIndex != BreakIterator.DONE) {
            animatedTitle = fullTitle.substring(0, nextIndex)
            nextIndex = breakIterator.next()
            delay(16)
        }

        launchProgress = 0.22f
        delay(140)

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            launchProgress = 1f
            delay(120)
            onNavigate("auth")
            return@LaunchedEffect
        }

        launchProgress = 0.38f

        val uid = user.uid
        val deviceId = DeviceId.get(context)

        val sessionWritten = runCatching {
            writeSessionAwait(uid, deviceId)
        }.getOrDefault(false)

        Log.d("LaunchScreen", "Session refresh at launch = $sessionWritten")
        launchProgress = 0.58f

        val activeDeviceId = runCatching {
            FirebaseDatabase.getInstance().reference
                .child("userSessions")
                .child(uid)
                .child("activeDeviceId")
                .get()
                .await()
                .getValue(String::class.java)
        }.getOrNull()

        if (!activeDeviceId.isNullOrBlank() && activeDeviceId != deviceId) {
            auth.signOut()
            launchProgress = 1f
            delay(120)
            onNavigate("auth")
            return@LaunchedEffect
        }

        launchProgress = 0.78f

        val completed = runCatching {
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(uid)
                .child("profileCompleted")
                .get()
                .await()
                .getValue(Boolean::class.java) ?: false
        }.getOrDefault(false)

        launchProgress = 1f
        delay(170)

        if (completed) {
            onNavigate("main")
        } else {
            onNavigate("profile")
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight

            val horizontalPadding = (screenWidth * 0.06f).coerceIn(16.dp, 36.dp)
            val logoSize = (screenWidth * 0.70f).coerceIn(220.dp, 360.dp)
            val glowSizeOuter = (screenWidth * 0.40f).coerceIn(140.dp, 220.dp)
            val glowSizeInner = (screenWidth * 0.26f).coerceIn(96.dp, 150.dp)
            val brandSpacing = (screenHeight * 0.018f).coerceIn(8.dp, 18.dp)
            val accentBarWidth = (screenWidth * 0.42f).coerceIn(120.dp, 180.dp)
            val accentBarHeight = 5.dp
            val cursorWidth = 2.dp
            val cursorHeight = when {
                screenWidth < 360.dp -> 16.dp
                screenWidth < 600.dp -> 18.dp
                else -> 20.dp
            }

            val titleStyle = when {
                screenWidth < 360.dp -> MaterialTheme.typography.headlineSmall
                screenWidth < 600.dp -> MaterialTheme.typography.headlineMedium
                else -> MaterialTheme.typography.displaySmall
            }

            val animatedTitleStyle = when {
                screenWidth < 360.dp -> MaterialTheme.typography.bodySmall
                screenWidth < 600.dp -> MaterialTheme.typography.bodyMedium
                else -> MaterialTheme.typography.titleMedium
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPadding),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(glowSizeOuter)
                        .alpha(glowAlpha * 0.75f)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .size(glowSizeInner)
                        .alpha(glowAlpha)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(animationSpec = tween(700)) +
                            slideInVertically(
                                initialOffsetY = { it / 10 },
                                animationSpec = tween(700, easing = FastOutSlowInEasing)
                            ) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(700, easing = FastOutSlowInEasing)
                            ),
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.cobfa_icon),
                            contentDescription = "CoB-FA Logo",
                            modifier = Modifier
                                .size(logoSize)
                                .scale(logoScale.value * pulseScale)
                                .alpha(logoAlpha.value)
                        )

                        Spacer(modifier = Modifier.size(brandSpacing))

                        Text(
                            text = "CoB-FA",
                            style = titleStyle,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.size((brandSpacing * 0.7f).coerceIn(6.dp, 12.dp)))

                        Box(
                            modifier = Modifier
                                .width(accentBarWidth)
                                .height(accentBarHeight)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                                            ),
                                            start = Offset.Zero,
                                            end = Offset(accentBarWidth.value * shimmerProgress, 0f)
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.size((brandSpacing * 0.9f).coerceIn(8.dp, 14.dp)))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = animatedTitle,
                                style = animatedTitleStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(width = cursorWidth, height = cursorHeight)
                                    .alpha(cursorAlpha)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}
