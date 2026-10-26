package com.calorietracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorietracker.app.ui.theme.PrimaryBlue
import com.calorietracker.app.ui.theme.SurfaceCard
import com.calorietracker.app.ui.theme.TextSecondary

private val QUICK_ADD_ML = listOf(250, 500)

/** Today's water intake with one-tap quick-add buttons. */
@Composable
fun WaterCard(
  liters: Float,
  targetLiters: Float,
  onAdd: (ml: Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val progress = if (targetLiters > 0f) (liters / targetLiters).coerceIn(0f, 1f) else 0f
  Card(
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    shape = RoundedCornerShape(16.dp),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "Water", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
          text = "${"%.2f".format(liters)} / ${"%.1f".format(targetLiters)} L",
          color = TextSecondary,
          fontSize = 13.sp
        )
      }
      LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QUICK_ADD_ML.forEach { ml ->
          Button(
            onClick = { onAdd(ml) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
          ) {
            Text("+$ml ml", fontSize = 12.sp)
          }
        }
      }
    }
  }
}
