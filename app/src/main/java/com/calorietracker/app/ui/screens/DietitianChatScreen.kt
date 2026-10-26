package com.calorietracker.app.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorietracker.app.domain.model.ChatImage
import com.calorietracker.app.domain.model.ChatRole
import com.calorietracker.app.presentation.ChatMessageUi
import com.calorietracker.app.presentation.MealCardState
import com.calorietracker.app.ui.theme.*

private val SUGGESTIONS =
  listOf("2 rotis with dal and sabzi", "Log my usual protein shake", "What should I eat for dinner?")

@Composable
fun DietitianChatScreen(
  messages: List<ChatMessageUi>,
  isLoading: Boolean,
  pendingImage: ChatImage?,
  userName: String,
  onSend: (String) -> Unit,
  onPickImage: () -> Unit,
  onClearImage: () -> Unit,
  onSaveMeal: (Long) -> Unit,
  onOpenMemory: () -> Unit,
  onNewChat: () -> Unit
) {
  var input by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  LaunchedEffect(messages.size, isLoading) {
    val last = messages.size + if (isLoading) 1 else 0
    if (last > 0) listState.animateScrollToItem(last - 1)
  }

  Column(modifier = Modifier.fillMaxSize().imePadding()) {
    Row(
      modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Your Dietitian", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Ask, log meals, send photos", color = TextSecondary, fontSize = 12.sp)
      }
      IconButton(onClick = onOpenMemory) {
        Icon(Icons.Default.Psychology, contentDescription = "Memory", tint = AccentPurple)
      }
      IconButton(onClick = onNewChat, enabled = !isLoading && messages.isNotEmpty()) {
        Icon(Icons.Default.Refresh, contentDescription = "New chat", tint = TextSecondary)
      }
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      if (messages.isEmpty()) {
        item {
          EmptyState(userName = userName, onSuggestion = { onSend(it) })
        }
      }
      items(messages, key = { it.id }) { msg ->
        MessageBubble(msg = msg, onSaveMeal = { onSaveMeal(msg.id) })
      }
      if (isLoading) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrimaryBlue)
            Spacer(Modifier.width(8.dp))
            Text("Dietitian is thinking…", color = TextSecondary, fontSize = 13.sp)
          }
        }
      }
    }

    pendingImage?.let { img ->
      Box(modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)) {
        DecodedImage(img, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
        IconButton(
          onClick = onClearImage,
          modifier = Modifier.align(Alignment.TopEnd).size(22.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
          Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(14.dp))
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = onPickImage, enabled = !isLoading) {
        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach photo", tint = PrimaryBlue)
      }
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Describe a meal or answer…", color = TextSecondary) },
        maxLines = 4,
        shape = RoundedCornerShape(20.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = SurfaceCard
          )
      )
      val canSend = !isLoading && (input.isNotBlank() || pendingImage != null)
      IconButton(
        onClick = {
          onSend(input)
          input = ""
        },
        enabled = canSend
      ) {
        Icon(Icons.Default.Send, contentDescription = "Send", tint = if (canSend) PrimaryBlue else TextSecondary)
      }
    }
  }
}

@Composable
private fun EmptyState(userName: String, onSuggestion: (String) -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text("Hi $userName 👋", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(
      "Tell me what you ate or snap a photo. I'll ask if something's unclear, and I remember your supplements and habits.",
      color = TextSecondary,
      fontSize = 14.sp
    )
    Spacer(Modifier.height(16.dp))
    SUGGESTIONS.forEach { s ->
      AssistChip(
        onClick = { onSuggestion(s) },
        label = { Text(s) },
        modifier = Modifier.padding(vertical = 2.dp),
        colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceCard, labelColor = TextPrimary)
      )
    }
  }
}

@Composable
private fun MessageBubble(msg: ChatMessageUi, onSaveMeal: () -> Unit) {
  val isUser = msg.role == ChatRole.USER
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    val bubbleColor =
      when {
        msg.isError -> Color(0xFF4A1F28)
        isUser -> PrimaryBlue
        else -> SurfaceCard
      }
    Column(
      modifier =
        Modifier.widthIn(max = 320.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(bubbleColor)
          .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      msg.image?.let { DecodedImage(it, Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp))) }
      if (msg.text.isNotBlank()) {
        Text(msg.text, color = if (isUser) Color.White else TextPrimary, fontSize = 15.sp)
      }
      if (msg.questions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          msg.questions.forEachIndexed { i, q ->
            Text("${i + 1}. $q", color = AccentOrange, fontSize = 14.sp, fontWeight = FontWeight.Medium)
          }
        }
      }
    }

    msg.meal?.let { meal ->
      Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(0.92f)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(meal.foodName, color = TextPrimary, fontWeight = FontWeight.Bold)
          Text(meal.portionDescription, color = TextSecondary, fontSize = 12.sp)
          Text("${meal.calories} kcal", color = AccentEmerald, fontSize = 22.sp, fontWeight = FontWeight.Bold)
          Text(
            "P ${"%.0f".format(meal.proteinGrams)}g · C ${"%.0f".format(meal.carbsGrams)}g · F ${"%.0f".format(meal.fatGrams)}g · ${meal.dateIso}",
            color = TextSecondary,
            fontSize = 12.sp
          )
          Spacer(Modifier.height(4.dp))
          when (msg.mealState) {
            MealCardState.PENDING ->
              Button(onClick = onSaveMeal, modifier = Modifier.fillMaxWidth()) { Text("Save to log") }
            MealCardState.SAVED -> Text("✓ Saved to your log", color = AccentEmerald, fontSize = 13.sp)
            MealCardState.SUPERSEDED ->
              Text("Replaced by a newer estimate", color = TextSecondary, fontSize = 13.sp)
          }
        }
      }
    }

    msg.memoryNotes.forEach { note ->
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(12.dp)).background(AccentPurple.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.Psychology, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(note, color = AccentPurple, fontSize = 12.sp)
      }
    }
  }
}

@Composable
private fun DecodedImage(image: ChatImage, modifier: Modifier) {
  val bitmap =
    remember(image.base64) {
      runCatching {
          val bytes = Base64.decode(image.base64, Base64.DEFAULT)
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        .getOrNull()
    }
  if (bitmap != null) {
    Image(bitmap = bitmap, contentDescription = "Meal photo", modifier = modifier, contentScale = ContentScale.Crop)
  }
}
