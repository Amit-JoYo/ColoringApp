# Save and Export Feature Documentation

## Overview
The Save and Export feature allows users to save their colored artworks to the device gallery and share them with others through various apps and platforms.

## Features

### 1. Save to Gallery
- **Description**: Saves the current colored image directly to the device's Pictures/ColoringApp folder
- **File Format**: PNG with 100% quality
- **Naming**: Automatically generates unique filename with timestamp (e.g., `ColoringApp_1699027200000.png`)
- **Location**: 
  - Android 10+ (API 29+): `Pictures/ColoringApp/` using MediaStore
  - Android 9 and below: `/storage/emulated/0/Pictures/ColoringApp/`

### 2. Share
- **Description**: Shares the colored artwork with other apps
- **Supported Apps**: Any app that can receive images (WhatsApp, Instagram, Email, etc.)
- **Format**: PNG image
- **Implementation**: Uses Android's share sheet with FileProvider

## Implementation Details

### Architecture

#### ViewModel (PaintingViewModel.kt)
```kotlin
// New state flow for save status
private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
val saveStatus = _saveStatus.asStateFlow()

// Save operation
fun saveImageToGallery(context: Context)

// Share operation  
fun shareImage(context: Context): Intent?

// Reset status
fun resetSaveStatus()
```

#### SaveStatus Sealed Class
```kotlin
sealed class SaveStatus {
    object Idle : SaveStatus()
    object Saving : SaveStatus()
    data class Success(val uri: Uri) : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}
```

### UI Components

#### Save Button
- **Icon**: Material save icon (floppy disk)
- **Behavior**: Shows circular progress indicator while saving
- **Feedback**: Toast message on success/error
- **Location**: Control bar at bottom of canvas

#### Share Button
- **Icon**: Material share icon
- **Behavior**: Opens Android share sheet
- **Location**: Control bar at bottom of canvas

### Permissions

#### AndroidManifest.xml
```xml
<!-- For Android 9 and below -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
    
<!-- For Android 10+ -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

#### FileProvider Configuration
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

## User Experience

### Save Flow
1. User taps the save icon
2. Button shows loading indicator
3. Image is saved to gallery
4. Toast message confirms success
5. Button returns to normal state

### Share Flow
1. User taps the share icon
2. Android share sheet appears instantly
3. User selects target app
4. Image is shared with selected app

## Error Handling

### Common Errors
- **No Image**: "No image to save" - Prevents saving when no image is loaded
- **Storage Full**: System error message about insufficient storage
- **Permission Denied**: Android system handles permission requests

### Error Messages
All errors are displayed as Toast messages with appropriate descriptions:
- "Image saved successfully!" (Success)
- "Error: {error message}" (Failure)

## File Management

### Save Location Strategy
- **Android 10+**: Uses MediaStore API (scoped storage)
  - No explicit permissions required
  - Files accessible in Photos/Gallery app
  - Organized in dedicated ColoringApp folder

- **Android 9-**: Traditional file system access
  - Requires WRITE_EXTERNAL_STORAGE permission
  - Files saved to external Pictures directory
  - Media scanner notified for gallery visibility

### Share Implementation
- **Temporary Files**: Created in app's cache directory (`cache/images/`)
- **Cleanup**: Automatically managed by Android system
- **Security**: FileProvider ensures secure URI access
- **Naming**: Timestamped to avoid conflicts

## Performance Considerations

### Async Operations
- All save/share operations run on IO dispatcher
- UI remains responsive during file operations
- Coroutines handle background work

### Memory Management
- Bitmap compression uses PNG format at 100% quality
- File streams properly closed with `use` blocks
- Temporary files cleaned up by system

## Testing

### Test Scenarios
1. **Save on Android 10+**: Verify MediaStore usage
2. **Save on Android 9-**: Verify traditional file system
3. **Share to various apps**: WhatsApp, Email, Drive, etc.
4. **Error handling**: No image, storage full, etc.
5. **Multiple saves**: Verify unique filenames
6. **Large images**: Test performance with high-res images

### Verification Steps
1. Save image and check gallery app
2. Navigate to Pictures/ColoringApp folder
3. Verify file exists and is viewable
4. Share and verify image received in target app
5. Check for duplicate filenames with rapid saves

## API Reference

### saveImageToGallery(context: Context)
Saves the current image to device gallery.

**Parameters:**
- `context`: Android Context for accessing content resolver

**Side Effects:**
- Updates `saveStatus` state flow
- Creates file in Pictures/ColoringApp
- Shows toast notification

**Threading:** Runs on IO dispatcher

### shareImage(context: Context): Intent?
Creates a share intent for the current image.

**Parameters:**
- `context`: Android Context for FileProvider

**Returns:**
- `Intent?`: Share intent or null on error

**Side Effects:**
- Creates temporary file in cache
- Returns configured share intent

**Threading:** Synchronous (should be quick)

## Future Enhancements

### Planned Features
1. **Quality Settings**: Let users choose PNG vs JPEG and quality
2. **Batch Export**: Export multiple versions at once
3. **Custom Naming**: Allow users to name their artworks
4. **Auto-Save**: Periodic automatic saves
5. **Export Formats**: Support for PDF, SVG, etc.
6. **Social Media Integration**: Direct posting to Instagram, Facebook
7. **Cloud Backup**: Automatic backup to cloud storage
8. **Watermark Option**: Add watermark to shared images

### Quality Improvements
1. **Progress Dialog**: Replace toast with better feedback
2. **Permission Handling**: Better explanation of permissions needed
3. **Save History**: Track saved images with thumbnails
4. **Undo Save**: Ability to delete recently saved images
5. **Image Metadata**: Add EXIF data with app information

## Troubleshooting

### Issue: Images not appearing in gallery
**Solution:** 
- Android 9-: Ensure WRITE_EXTERNAL_STORAGE permission granted
- Check if media scanner ran (restart device if needed)
- Verify Pictures/ColoringApp folder exists

### Issue: Share fails to send
**Solution:**
- Verify FileProvider configuration in manifest
- Check file_paths.xml exists with correct paths
- Ensure target app supports PNG images

### Issue: "No image to save" error
**Solution:**
- Ensure an image is loaded before trying to save
- Check if image processing completed successfully

## Version History

### v1.1.0 (November 1, 2025)
- ✨ Added save to gallery functionality
- ✨ Added share to other apps functionality
- 🎨 Added save and share icons
- 🔧 Configured FileProvider for secure sharing
- 📱 Support for Android 7.0 (API 24) to Android 14 (API 34)
- ⚡ Async operations for smooth UX
- 🐛 Error handling with user feedback

---

**Last Updated**: November 1, 2025  
**Version**: 1.1.0  
**Feature Status**: ✅ Implemented and Tested
