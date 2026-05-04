package com.example.demo.ui

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontStyle

/**
 * 游戏主页面共用的动态氛围背景。
 */
@Composable
fun StudioBackground() {
    val infinite = rememberInfiniteTransition()

    val far by infinite.animateFloat(0f, 1000f, infiniteRepeatable(tween(40000)))
    val mid by infinite.animateFloat(0f, 1000f, infiniteRepeatable(tween(28000)))
    val near by infinite.animateFloat(0f, 1000f, infiniteRepeatable(tween(18000)))

    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFFDF8F2), Color(0xFFF7F1E8))))

        drawCircle(Color.White.copy(0.03f), size.width * 0.9f, Offset(far % size.width, size.height * 0.25f))

        drawCircle(Color.White.copy(0.04f), size.width * 0.7f, Offset(mid % size.width, size.height * 0.5f))

        drawCircle(Color.White.copy(0.06f), size.width * 0.5f, Offset(near % size.width, size.height * 0.7f))
    }
}

/**
 * 自定义视觉体系中的半透明玻璃容器。
 */
@Composable
fun StudioGlass(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color(0xFFE6D3A3).copy(alpha = 0.4f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        content()
    }
}

/**
 * 用于交互控件背后的呼吸光效。
 */
@Composable
fun StudioGlow(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition()
    val alpha by infinite.animateFloat(0.15f, 0.55f, infiniteRepeatable(tween(1800), RepeatMode.Reverse))

    Box(
        modifier.background(
            Brush.radialGradient(listOf(Color(0xFFD4AF37).copy(alpha), Color.Transparent))
        )
    )
}

/**
 * 全屏点击粒子反馈层。
 */
@Composable
fun ClickParticleLayer() {
    var particles by remember { mutableStateOf(listOf<Offset>()) }
    val infinite = rememberInfiniteTransition()

    val progress by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(800)))

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    particles = particles + offset
                }
            }
    ) {
        particles.forEach { origin ->
            repeat(8) { i ->
                val angle = i * 45f
                val dx = kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
                val dy = kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()

                drawCircle(
                    color = Color(0xFFD4AF37).copy(alpha = 1f - progress),
                    radius = 6f * (1f - progress),
                    center = Offset(
                        origin.x + dx * progress * 120,
                        origin.y + dy * progress * 120
                    )
                )
            }
        }
    }
}

/**
 * 匹配玻璃视觉风格的自定义按钮。
 */
@Composable
fun StudioButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f)

    Box(modifier.scale(scale)) {
        StudioGlow(Modifier.matchParentSize())

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(0.7f))
                .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                            onClick()
                        }
                    )
                }
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color(0xFF7A5C2E), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

/**
 * 等待其他玩家或网络状态时使用的紧凑加载卡片。
 */
@Composable
fun WaitingCard(message: String) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Color(0x22FFFFFF), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x44D4AF37), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFFD4AF37),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = Color(0xFF8B7355),
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic
            )
        }
    }
}
