package com.example.coloringapp

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private const val PREFS_NAME = "coloring_app_settings"
private const val KEY_GEMINI_API_KEY = "gemini_api_key"
private const val KEY_AI_PROVIDER = "ai_provider"

enum class AIProvider(val displayName: String, val description: String) {
    GEMINI("Google Gemini", "Uses Gemini 2.0 Flash for image generation"),
    LOCAL("Local Processing", "Uses on-device edge detection (no API needed)")
}

/**
 * Get the saved Gemini API key
 */
fun getGeminiApiKey(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
}

/**
 * Save the Gemini API key
 */
fun saveGeminiApiKey(context: Context, apiKey: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply()
}

/**
 * Get the selected AI provider
 */
fun getAIProvider(context: Context): AIProvider {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val providerName = prefs.getString(KEY_AI_PROVIDER, AIProvider.LOCAL.name) ?: AIProvider.LOCAL.name
    return try {
        AIProvider.valueOf(providerName)
    } catch (e: Exception) {
        AIProvider.LOCAL
    }
}

/**
 * Save the selected AI provider
 */
fun saveAIProvider(context: Context, provider: AIProvider) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_AI_PROVIDER, provider.name).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(getGeminiApiKey(context)) }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(getAIProvider(context)) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Provider Section
            Text(
                text = "AI Provider for Color by Number",
                style = MaterialTheme.typography.titleMedium
            )
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AIProvider.values().forEach { provider ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProvider == provider,
                                onClick = {
                                    selectedProvider = provider
                                    saveAIProvider(context, provider)
                                }
                            )
                            Column(
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = provider.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Gemini API Key Section
            if (selectedProvider == AIProvider.GEMINI) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Gemini API Key",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Get your free API key from Google AI Studio:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "https://aistudio.google.com/apikey",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key") },
                            placeholder = { Text("Enter your Gemini API key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showApiKey) 
                                VisualTransformation.None 
                            else 
                                PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                TextButton(onClick = { showApiKey = !showApiKey }) {
                                    Text(if (showApiKey) "Hide" else "Show")
                                }
                            },
                            singleLine = true
                        )
                        
                        Button(
                            onClick = {
                                saveGeminiApiKey(context, apiKey)
                                showSaveConfirmation = true
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save API Key")
                        }
                    }
                }
            }
            
            // Info Section
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "How Color by Number Works",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = """
                            • With Gemini: Your image is sent to Google's AI which creates a professional color-by-number version with clean outlines and numbered regions.
                            
                            • With Local Processing: Uses on-device edge detection to create outlines. Works offline but may produce less refined results.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    
    // Save confirmation dialog
    if (showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { Text("Saved") },
            text = { Text("Your API key has been saved securely.") },
            confirmButton = {
                TextButton(onClick = { showSaveConfirmation = false }) {
                    Text("OK")
                }
            }
        )
    }
}
