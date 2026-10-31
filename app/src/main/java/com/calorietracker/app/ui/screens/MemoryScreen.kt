package com.calorietracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorietracker.app.data.local.MemoryCategory
import com.calorietracker.app.data.local.MemoryEntity
import com.calorietracker.app.ui.theme.*

/** Lets the user see, add, edit and delete everything the dietitian remembers about them. */
@Composable
fun MemoryScreen(
  memories: List<MemoryEntity>,
  onAdd: (category: String, content: String) -> Unit,
  onEdit: (id: Long, category: String, content: String) -> Unit,
  onDelete: (Long) -> Unit,
  onBack: () -> Unit
) {
  var editing by remember { mutableStateOf<MemoryEntity?>(null) }
  var showNew by remember { mutableStateOf(false) }
  var pendingDelete by remember { mutableStateOf<MemoryEntity?>(null) }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(SurfaceDark)
          .padding(horizontal = 8.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
      }
      Column(modifier = Modifier.weight(1f)) {
        Text("What I remember", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Your dietitian uses this in every reply", color = TextSecondary, fontSize = 12.sp)
      }
      IconButton(onClick = { showNew = true }) {
        Icon(Icons.Default.Add, contentDescription = "Add memory", tint = PrimaryBlue)
      }
    }

    if (memories.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
          "Nothing yet. Add things like your protein powder and scoop size, supplements, allergies, or habits, or just mention them in chat and I'll remember.",
          color = TextSecondary
        )
      }
    } else {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(memories, key = { it.id }) { memory ->
          Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
            Row(
              modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  memory.category.uppercase(),
                  color = AccentPurple,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(memory.content, color = TextPrimary, fontSize = 15.sp)
              }
              IconButton(onClick = { editing = memory }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary)
              }
              IconButton(onClick = { pendingDelete = memory }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentOrange)
              }
            }
          }
        }
      }
    }
  }

  if (showNew) {
    MemoryEditDialog(
      initial = null,
      onDismiss = { showNew = false },
      onSave = { category, content ->
        onAdd(category, content)
        showNew = false
      }
    )
  }
  editing?.let { m ->
    MemoryEditDialog(
      initial = m,
      onDismiss = { editing = null },
      onSave = { category, content ->
        onEdit(m.id, category, content)
        editing = null
      }
    )
  }
  pendingDelete?.let { m ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("Forget this?") },
      text = { Text(m.content) },
      confirmButton = {
        TextButton(
          onClick = {
            onDelete(m.id)
            pendingDelete = null
          }
        ) {
          Text("Forget")
        }
      },
      dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
    )
  }
}

@Composable
private fun MemoryEditDialog(
  initial: MemoryEntity?,
  onDismiss: () -> Unit,
  onSave: (category: String, content: String) -> Unit
) {
  var category by remember { mutableStateOf(initial?.category ?: MemoryCategory.SUPPLEMENT) }
  var content by remember { mutableStateOf(initial?.content ?: "") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "Add memory" else "Edit memory") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          MemoryCategory.all.forEach { c ->
            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
          }
        }
        OutlinedTextField(
          value = content,
          onValueChange = { content = it },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text("e.g. Protein powder: <brand>, 1 scoop = 33 g, after workouts") },
          minLines = 2,
          maxLines = 5,
          shape = RoundedCornerShape(12.dp)
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onSave(category, content) }, enabled = content.isNotBlank()) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}
