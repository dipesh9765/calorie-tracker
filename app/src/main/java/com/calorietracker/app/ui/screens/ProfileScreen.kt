package com.calorietracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.ui.theme.*

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    onSaveProfile: (UserProfile) -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit
) {
    var selectedProvider by remember { mutableStateOf(userProfile.primaryAiProvider) }
    var geminiKey by remember { mutableStateOf(userProfile.geminiApiKey) }
    var openAiKey by remember { mutableStateOf(userProfile.openAiApiKey) }
    var claudeKey by remember { mutableStateOf(userProfile.claudeApiKey) }
    var deepSeekKey by remember { mutableStateOf(userProfile.deepSeekApiKey) }

    var geminiModel by remember { mutableStateOf(userProfile.geminiModel) }
    var openAiModel by remember { mutableStateOf(userProfile.openAiModel) }
    var claudeModel by remember { mutableStateOf(userProfile.claudeModel) }
    var deepSeekModel by remember { mutableStateOf(userProfile.deepSeekModel) }

    var currentWeight by remember { mutableStateOf(userProfile.weightKg.toString()) }
    var targetWeight by remember { mutableStateOf(userProfile.targetWeightKg.toString()) }
    var userHeight by remember { mutableStateOf(userProfile.heightCm.toString()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Profile & Settings",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "TDEE / Deficit Calculator & AI Key Settings",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }

        // Nutrition Metrics Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = "Profile", tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Personal Baseline (25 yrs, Male, 188cm)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("BMR", color = TextSecondary, fontSize = 12.sp)
                            Text("${userProfile.bmrCalories} kcal", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("TDEE (Maintenance)", color = TextSecondary, fontSize = 12.sp)
                            Text("${userProfile.tdeeMaintenanceCalories} kcal", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Target Deficit", color = TextSecondary, fontSize = 12.sp)
                            Text("${userProfile.targetDailyCalories} kcal", color = AccentEmerald, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Weight Loss Goal: 105 kg → 90 kg (600 kcal deficit + 180g protein)",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // AI Provider Settings Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = "AI Keys", tint = AccentOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Handshake & Provider Keys", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Text(
                        text = "Select active AI provider and enter your API Key:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    val providers = listOf("Gemini", "OpenAI", "Claude", "DeepSeek")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        providers.forEach { provider ->
                            FilterChip(
                                selected = selectedProvider == provider,
                                onClick = { selectedProvider = provider },
                                label = { Text(provider, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (selectedProvider) {
                        "Gemini" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = geminiKey,
                                onValueChange = { geminiKey = it },
                                label = { Text("Gemini API Key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = geminiModel,
                                onValueChange = { geminiModel = it },
                                label = { Text("Gemini Model Name") },
                                placeholder = { Text("e.g. gemini-3.6-flash, gemini-3.5-flash") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        "OpenAI" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = openAiKey,
                                onValueChange = { openAiKey = it },
                                label = { Text("OpenAI API Key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = openAiModel,
                                onValueChange = { openAiModel = it },
                                label = { Text("OpenAI Model Name") },
                                placeholder = { Text("e.g. gpt-4o-mini, gpt-4o") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        "Claude" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = claudeKey,
                                onValueChange = { claudeKey = it },
                                label = { Text("Claude API Key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = claudeModel,
                                onValueChange = { claudeModel = it },
                                label = { Text("Claude Model Name") },
                                placeholder = { Text("e.g. claude-3-5-sonnet-20240620") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        "DeepSeek" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = deepSeekKey,
                                onValueChange = { deepSeekKey = it },
                                label = { Text("DeepSeek API Key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = deepSeekModel,
                                onValueChange = { deepSeekModel = it },
                                label = { Text("DeepSeek Model Name") },
                                placeholder = { Text("e.g. deepseek-chat") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val updated = userProfile.copy(
                                primaryAiProvider = selectedProvider,
                                geminiApiKey = geminiKey,
                                openAiApiKey = openAiKey,
                                claudeApiKey = claudeKey,
                                deepSeekApiKey = deepSeekKey,
                                geminiModel = geminiModel,
                                openAiModel = openAiModel,
                                claudeModel = claudeModel,
                                deepSeekModel = deepSeekModel,
                                weightKg = currentWeight.toFloatOrNull() ?: 105f,
                                targetWeightKg = targetWeight.toFloatOrNull() ?: 90f,
                                heightCm = userHeight.toFloatOrNull() ?: 188f
                            )
                            onSaveProfile(updated)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Save Configuration", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Data Export Section Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Export", tint = AccentEmerald)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Local Data Backup & Export", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Text(
                        text = "Export all your meal logs & nutrition records to device storage:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportJson,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard)
                        ) {
                            Text("Export JSON", color = Color.White)
                        }

                        Button(
                            onClick = onExportCsv,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard)
                        ) {
                            Text("Export CSV", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
