package com.example.coloringapp

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.coloringapp.audio.MusicManager
import com.example.coloringapp.audio.SoundEffectManager
import com.example.coloringapp.utils.LanguageManager

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
                            Text(stringResource(R.string.save_api_key))
                        }
                    }
                }
            }
            
            // Language Section
            Spacer(modifier = Modifier.height(16.dp))
            LanguageSection()
            
            // Audio Section
            Spacer(modifier = Modifier.height(16.dp))
            AudioSection()
            
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
                        text = stringResource(R.string.how_color_by_number_works),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.color_by_number_explanation),
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
            title = { Text(stringResource(R.string.saved)) },
            text = { Text(stringResource(R.string.api_key_saved)) },
            confirmButton = {
                TextButton(onClick = { showSaveConfirmation = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSection() {
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(LanguageManager.getSelectedLanguage(context)) }
    var expanded by remember { mutableStateOf(false) }
    
    Text(
        text = stringResource(R.string.language),
        style = MaterialTheme.typography.titleMedium
    )
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌐",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedLanguage.nativeName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.select_language)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        LanguageManager.Language.entries.forEach { language ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(language.nativeName)
                                        if (language != LanguageManager.Language.SYSTEM) {
                                            Text(
                                                text = language.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedLanguage = language
                                    LanguageManager.setLanguage(context, language)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            Text(
                text = stringResource(R.string.language_change_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioSection() {
    val context = LocalContext.current
    
    // Music state
    val musicEnabled by MusicManager.musicEnabled.collectAsState()
    val musicVolume by MusicManager.volume.collectAsState()
    val musicSource by MusicManager.musicSource.collectAsState()
    val hasOfflineMusic by MusicManager.hasOfflineMusic.collectAsState()
    val isYouTubeMusicInstalled = remember { MusicManager.isYouTubeMusicInstalled(context) }
    
    // Sound effects state
    val sfxEnabled by SoundEffectManager.enabled.collectAsState()
    val sfxVolume by SoundEffectManager.volume.collectAsState()
    
    Text(
        text = stringResource(R.string.audio),
        style = MaterialTheme.typography.titleMedium
    )
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Music Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎵",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.background_music),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = musicEnabled,
                    onCheckedChange = { MusicManager.setEnabled(it) }
                )
            }
            
            if (musicEnabled) {
                // Music volume slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.volume),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp)
                    )
                    Slider(
                        value = musicVolume,
                        onValueChange = { MusicManager.setVolume(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Music source selection
                Text(
                    text = stringResource(R.string.music_source),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Column {
                    // Offline option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = musicSource == MusicManager.MusicSource.OFFLINE,
                            onClick = { 
                                if (hasOfflineMusic) {
                                    MusicManager.setMusicSource(MusicManager.MusicSource.OFFLINE) 
                                }
                            },
                            enabled = hasOfflineMusic
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = MusicManager.MusicSource.OFFLINE.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasOfflineMusic) 
                                    MaterialTheme.colorScheme.onSurface 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!hasOfflineMusic) {
                                Text(
                                    text = stringResource(R.string.no_offline_music),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // YouTube Music option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = musicSource == MusicManager.MusicSource.YOUTUBE_MUSIC,
                            onClick = { MusicManager.setMusicSource(MusicManager.MusicSource.YOUTUBE_MUSIC) }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = MusicManager.MusicSource.YOUTUBE_MUSIC.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isYouTubeMusicInstalled) 
                                    stringResource(R.string.youtube_music_installed) 
                                else 
                                    stringResource(R.string.youtube_music_browser),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Play music button
                Button(
                    onClick = { MusicManager.launchYouTubeMusic() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.play_relaxing_music))
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Sound Effects Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔊",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.sound_effects),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = sfxEnabled,
                    onCheckedChange = { SoundEffectManager.setEnabled(it) }
                )
            }
            
            if (sfxEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.volume),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp)
                    )
                    Slider(
                        value = sfxVolume,
                        onValueChange = { SoundEffectManager.setVolume(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
