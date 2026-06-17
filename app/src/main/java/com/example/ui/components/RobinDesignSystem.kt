package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

@Composable
fun GeometricRobinBirdCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val minSize = minOf(width, height)
        
        // Scale and center translation
        clipRect {
            val scale = minSize / 100f
            val dx = (width - minSize) / 2f
            val dy = (height - minSize) / 2f
            
            // Draw Branch (Teal/Forest green)
            val branchColor = Color(0xFF0D5C3A)
            val pathBranch = Path().apply {
                moveTo(dx + 25 * scale, dy + 70 * scale)
                lineTo(dx + 65 * scale, dy + 70 * scale)
                lineTo(dx + 75 * scale, dy + 62 * scale)
                lineTo(dx + 45 * scale, dy + 64 * scale)
                close()
            }
            drawPath(pathBranch, branchColor)

            // Draw Tail (White)
            val pathTail = Path().apply {
                moveTo(dx + 12 * scale, dy + 62 * scale)
                lineTo(dx + 22 * scale, dy + 44 * scale)
                lineTo(dx + 19 * scale, dy + 41 * scale)
                lineTo(dx + 7 * scale, dy + 59 * scale)
                close()
            }
            drawPath(pathTail, Color.White)

            // Draw Wing (Vibrant Orange Coral)
            val wingColor = Color(0xFFF05A28)
            val pathWing = Path().apply {
                moveTo(dx + 35 * scale, dy + 40 * scale)
                lineTo(dx + 12 * scale, dy + 62 * scale)
                lineTo(dx + 41 * scale, dy + 51 * scale)
                close()
            }
            drawPath(pathWing, wingColor)

            // Draw Lower Breast (Pearl White)
            val lowerBreast = Path().apply {
                moveTo(dx + 41 * scale, dy + 51 * scale)
                lineTo(dx + 22 * scale, dy + 67 * scale)
                lineTo(dx + 36 * scale, dy + 67 * scale)
                lineTo(dx + 52 * scale, dy + 52 * scale)
                close()
            }
            drawPath(lowerBreast, Color(0xFFF0F2F0))

            // Draw Upper Breast & Forehead (Bold Robin Red-Orange)
            val breastRed = Color(0xFFD9381E)
            val pathBreast = Path().apply {
                moveTo(dx + 41 * scale, dy + 51 * scale)
                lineTo(dx + 35 * scale, dy + 40 * scale)
                lineTo(dx + 54 * scale, dy + 32 * scale)
                lineTo(dx + 55 * scale, dy + 44 * scale)
                lineTo(dx + 50 * scale, dy + 52 * scale)
                close()
            }
            drawPath(pathBreast, breastRed)

            // Head Back (Light grey)
            val grayHead = Color(0xFFE2E6E2)
            val pathHead = Path().apply {
                moveTo(dx + 35 * scale, dy + 40 * scale)
                lineTo(dx + 28 * scale, dy + 36 * scale)
                lineTo(dx + 42 * scale, dy + 29 * scale)
                close()
            }
            drawPath(pathHead, grayHead)

            val whiteCrown = Path().apply {
                moveTo(dx + 42 * scale, dy + 29 * scale)
                lineTo(dx + 51 * scale, dy + 21 * scale)
                lineTo(dx + 54 * scale, dy + 32 * scale)
                close()
            }
            drawPath(whiteCrown, Color.White)

            // Beak
            val pathBeak = Path().apply {
                moveTo(dx + 54 * scale, dy + 25 * scale)
                lineTo(dx + 60 * scale, dy + 27 * scale)
                lineTo(dx + 54 * scale, dy + 29 * scale)
                close()
            }
            drawPath(pathBeak, Color(0xFFD9381E))
        }
    }
}

@Composable
fun CleanPulsatingSoundwaves(
    isPulsing: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "soundwave")
    
    val heightScale1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val heightScale2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val heightScale3 by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )
    val heightScale4 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bars = listOf(heightScale1, heightScale3, heightScale2, heightScale4, heightScale3, heightScale1)
        bars.forEach { value ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(4.dp)
                    .fillMaxHeight(if (isPulsing) value else 0.15f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
