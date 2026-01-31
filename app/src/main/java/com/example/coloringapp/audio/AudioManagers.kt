package com.example.coloringapp.audio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "AudioManagers"

/**
 * Singleton manager for background music playback.
 * Supports both offline music and YouTube Music integration.
 */
object MusicManager {
    
    private var mediaPlayer: MediaPlayer? = null
    private var context: Context? = null
    private var currentTrackName: String = "calm"
    
    // State flows for UI
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _volume = MutableStateFlow(0.7f)
    val volume: StateFlow<Float> = _volume
    
    private val _musicEnabled = MutableStateFlow(true)
    val musicEnabled: StateFlow<Boolean> = _musicEnabled
    
    private val _musicSource = MutableStateFlow(MusicSource.YOUTUBE_MUSIC) // Default to YouTube since no offline files
    val musicSource: StateFlow<MusicSource> = _musicSource
    
    private val _hasOfflineMusic = MutableStateFlow(false)
    val hasOfflineMusic: StateFlow<Boolean> = _hasOfflineMusic
    
    enum class MusicTrack(val resourceName: String, val displayName: String) {
        CALM("music_calm", "Calm & Relaxing"),
        MENU("music_menu", "Menu Theme"),
        NONE("", "No Music")
    }
    
    enum class MusicSource(val displayName: String) {
        OFFLINE("Offline Music"),
        YOUTUBE_MUSIC("YouTube Music")
    }
    
    private const val PREFS_NAME = "music_prefs"
    private const val KEY_ENABLED = "music_enabled"
    private const val KEY_VOLUME = "music_volume"
    private const val KEY_SOURCE = "music_source"
    
    /**
     * Initialize the music manager with context
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        checkOfflineMusicAvailable()
        loadPreferences()
    }
    
    /**
     * Check if offline music resources exist
     */
    private fun checkOfflineMusicAvailable() {
        context?.let { ctx ->
            val resId = ctx.resources.getIdentifier("music_calm", "raw", ctx.packageName)
            _hasOfflineMusic.value = resId != 0
            Log.d(TAG, "Offline music available: ${_hasOfflineMusic.value}")
        }
    }
    
    /**
     * Load saved preferences
     */
    private fun loadPreferences() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _musicEnabled.value = prefs.getBoolean(KEY_ENABLED, true)
            _volume.value = prefs.getFloat(KEY_VOLUME, 0.7f)
            
            val savedSource = prefs.getString(KEY_SOURCE, null)
            _musicSource.value = if (savedSource != null) {
                try {
                    MusicSource.valueOf(savedSource)
                } catch (e: Exception) {
                    if (_hasOfflineMusic.value) MusicSource.OFFLINE else MusicSource.YOUTUBE_MUSIC
                }
            } else {
                if (_hasOfflineMusic.value) MusicSource.OFFLINE else MusicSource.YOUTUBE_MUSIC
            }
        }
    }
    
    /**
     * Save preferences
     */
    private fun savePreferences() {
        context?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_ENABLED, _musicEnabled.value)
                putFloat(KEY_VOLUME, _volume.value)
                putString(KEY_SOURCE, _musicSource.value.name)
            }
        }
    }
    
    /**
     * Play background music
     */
    fun play(track: MusicTrack = MusicTrack.CALM) {
        if (!_musicEnabled.value) return
        
        when (_musicSource.value) {
            MusicSource.OFFLINE -> playOffline(track)
            MusicSource.YOUTUBE_MUSIC -> launchYouTubeMusic()
        }
    }
    
    /**
     * Play offline music from resources
     */
    private fun playOffline(track: MusicTrack) {
        val ctx = context ?: return
        
        if (track == MusicTrack.NONE || track.resourceName.isEmpty()) {
            stop()
            return
        }
        
        // Get resource ID dynamically
        val resId = ctx.resources.getIdentifier(track.resourceName, "raw", ctx.packageName)
        if (resId == 0) {
            Log.w(TAG, "Music resource not found: ${track.resourceName}")
            // Fall back to YouTube Music if no offline music
            if (isYouTubeMusicInstalled(ctx)) {
                _musicSource.value = MusicSource.YOUTUBE_MUSIC
                launchYouTubeMusic()
            }
            return
        }
        
        // Stop current playback
        stop()
        currentTrackName = track.resourceName
        
        try {
            mediaPlayer = MediaPlayer.create(ctx, resId)?.apply {
                isLooping = true
                setVolume(_volume.value, _volume.value)
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _isPlaying.value = false
                    true
                }
            }
            mediaPlayer?.start()
            _isPlaying.value = true
            Log.d(TAG, "Started playing: ${track.resourceName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play music", e)
            _isPlaying.value = false
        }
    }
    
    /**
     * Launch YouTube Music app with a relaxing playlist
     */
    fun launchYouTubeMusic() {
        val ctx = context ?: return
        
        try {
            // First try YouTube Music app
            if (isYouTubeMusicInstalled(ctx)) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://music.youtube.com/search?q=relaxing+coloring+music")
                    setPackage("com.google.android.apps.youtube.music")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                _isPlaying.value = true
            } else {
                // Open YouTube Music in browser
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://music.youtube.com/search?q=relaxing+coloring+music")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch YouTube Music", e)
        }
    }
    
    /**
     * Check if YouTube Music is installed
     */
    fun isYouTubeMusicInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(
                "com.google.android.apps.youtube.music",
                PackageManager.GET_ACTIVITIES
            )
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Pause music playback
     */
    fun pause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
    }
    
    /**
     * Resume music playback
     */
    fun resume() {
        if (_musicEnabled.value && _musicSource.value == MusicSource.OFFLINE) {
            try {
                mediaPlayer?.start()
                _isPlaying.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume music", e)
            }
        }
    }
    
    /**
     * Stop music completely
     */
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping music", e)
        }
        mediaPlayer = null
        _isPlaying.value = false
    }
    
    /**
     * Toggle music on/off
     */
    fun toggle() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }
    
    /**
     * Set music enabled state
     */
    fun setEnabled(enabled: Boolean) {
        _musicEnabled.value = enabled
        savePreferences()
        
        if (!enabled) {
            stop()
        }
    }
    
    /**
     * Set volume (0.0 to 1.0)
     */
    fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(_volume.value, _volume.value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume", e)
        }
        savePreferences()
    }
    
    /**
     * Set music source
     */
    fun setMusicSource(source: MusicSource) {
        stop()
        _musicSource.value = source
        savePreferences()
    }
    
    /**
     * Release resources
     */
    fun release() {
        stop()
        context = null
    }
}

/**
 * Manager for sound effects (taps, victory, etc.)
 */
object SoundEffectManager {
    
    private var soundPool: SoundPool? = null
    private var context: Context? = null
    private var soundsLoaded = false
    
    // Sound IDs (0 means not loaded)
    private var tapSoundId: Int = 0
    private var placeSoundId: Int = 0
    private var victorySoundId: Int = 0
    private var errorSoundId: Int = 0
    
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled
    
    private val _volume = MutableStateFlow(0.8f)
    val volume: StateFlow<Float> = _volume
    
    private val _hasSounds = MutableStateFlow(false)
    val hasSounds: StateFlow<Boolean> = _hasSounds
    
    private const val PREFS_NAME = "sfx_prefs"
    private const val KEY_ENABLED = "sfx_enabled"
    private const val KEY_VOLUME = "sfx_volume"
    
    /**
     * Initialize sound effects
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        loadPreferences()
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build().apply {
                setOnLoadCompleteListener { _, _, status ->
                    if (status == 0) {
                        soundsLoaded = true
                    }
                }
            }
        
        // Load sounds dynamically (won't crash if not found)
        loadSounds()
    }
    
    private fun loadSounds() {
        val ctx = context ?: return
        val pool = soundPool ?: return
        
        var hasSomeSounds = false
        
        // Try to load each sound
        ctx.resources.getIdentifier("sfx_tap", "raw", ctx.packageName).let {
            if (it != 0) {
                tapSoundId = pool.load(ctx, it, 1)
                hasSomeSounds = true
            }
        }
        
        ctx.resources.getIdentifier("sfx_place", "raw", ctx.packageName).let {
            if (it != 0) {
                placeSoundId = pool.load(ctx, it, 1)
                hasSomeSounds = true
            }
        }
        
        ctx.resources.getIdentifier("sfx_victory", "raw", ctx.packageName).let {
            if (it != 0) {
                victorySoundId = pool.load(ctx, it, 1)
                hasSomeSounds = true
            }
        }
        
        ctx.resources.getIdentifier("sfx_error", "raw", ctx.packageName).let {
            if (it != 0) {
                errorSoundId = pool.load(ctx, it, 1)
                hasSomeSounds = true
            }
        }
        
        _hasSounds.value = hasSomeSounds
        Log.d(TAG, "Sound effects available: $hasSomeSounds")
    }
    
    private fun loadPreferences() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _enabled.value = prefs.getBoolean(KEY_ENABLED, true)
            _volume.value = prefs.getFloat(KEY_VOLUME, 0.8f)
        }
    }
    
    private fun savePreferences() {
        context?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_ENABLED, _enabled.value)
                putFloat(KEY_VOLUME, _volume.value)
            }
        }
    }
    
    private fun playSound(soundId: Int) {
        if (_enabled.value && soundId != 0 && soundsLoaded) {
            soundPool?.play(soundId, _volume.value, _volume.value, 1, 0, 1f)
        }
    }
    
    /**
     * Play tap sound
     */
    fun playTap() = playSound(tapSoundId)
    
    /**
     * Play piece placement sound
     */
    fun playPlace() = playSound(placeSoundId)
    
    /**
     * Play victory sound
     */
    fun playVictory() = playSound(victorySoundId)
    
    /**
     * Play error sound
     */
    fun playError() = playSound(errorSoundId)
    
    /**
     * Set enabled state
     */
    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        savePreferences()
    }
    
    /**
     * Set volume
     */
    fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
        savePreferences()
    }
    
    /**
     * Release resources
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        context = null
        soundsLoaded = false
    }
}
