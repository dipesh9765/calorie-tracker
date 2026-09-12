package com.calorietracker.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorietracker.app.ui.theme.PrimaryBlue
import com.calorietracker.app.ui.theme.TextSecondary
import kotlin.math.sin

@Composable
fun LiquidBubbleProgress(currentCalories: Int, targetCalories: Int, modifier: Modifier = Modifier) {
  val progress = (currentCalories.toFloat() / targetCalories.toFloat()).coerceIn(0f, 1f)

  // Wave animation
  val infiniteTransition = rememberInfiniteTransition(label = "LiquidWave")
  val waveOffset by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 2f * Math.PI.toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(2000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart
        ),
      label = "WaveOffset"
    )

  Box(modifier = modifier.size(240.dp), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val width = size.width
      val height = size.height
      val radius = width / 2.2f
      val center = Offset(width / 2, height / 2)

      // Outer Circle Ring
      drawCircle(
        color = PrimaryBlue,
        radius = radius,
        center = center,
        style = Stroke(width = 6.dp.toPx())
      )

      // Clip inner circle for liquid fill
      val circlePath =
        Path().apply {
          addOval(
            androidx.compose.ui.geometry.Rect(
              center.x - radius + 6.dp.toPx(),
              center.y - radius + 6.dp.toPx(),
              center.x + radius - 6.dp.toPx(),
              center.y + radius - 6.dp.toPx()
            )
          )
        }

      clipPath(circlePath) {
        // Draw wave background fill
        val liquidHeight = (1f - progress) * (2 * radius) + (center.y - radius)
        val wavePath = Path()

        wavePath.moveTo(0f, height)
        wavePath.lineTo(0f, liquidHeight)

        var x = 0f
        while (x <= width) {
          val y =
            liquidHeight +
              sin((x / width * 4 * Math.PI + waveOffset).toDouble()).toFloat() * 12.dp.toPx()
          wavePath.lineTo(x, y)
          x += 10.dp.toPx()
        }

        wavePath.lineTo(width, height)
        wavePath.close()

        drawPath(path = wavePath, color = PrimaryBlue.copy(alpha = 0.25f))

        // Draw bubbles
        val bubbleCount = (progress * 25).toInt()
        for (i in 0 until bubbleCount) {
          val bubbleX = center.x + ((i * 37) % (radius.toInt() * 1.5f) - radius * 0.75f)
          val bubbleY = height - ((i * 43) % (radius.toInt() * 1.2f))
          val bubbleRadius = (4 + (i % 6)).dp.toPx()
          drawCircle(
            color = PrimaryBlue.copy(alpha = 0.6f),
            radius = bubbleRadius,
            center = Offset(bubbleX, bubbleY)
          )
        }
      }
    }

    // Center Numbers
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "$currentCalories",
        color = Color.White,
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold
      )
      Text(text = "of $targetCalories kcal", color = TextSecondary, fontSize = 14.sp)
      val remaining = (targetCalories - currentCalories).coerceAtLeast(0)
      Text(
        text = "$remaining kcal left",
        color = PrimaryBlue,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp)
      )
    }
  }
}
