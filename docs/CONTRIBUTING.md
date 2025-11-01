# Contributing to ColoringApp

Thank you for your interest in contributing to ColoringApp! This document provides guidelines and information for contributors to help maintain code quality and project consistency.

## Table of Contents
- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Code Standards](#code-standards)
- [Testing Guidelines](#testing-guidelines)
- [Pull Request Process](#pull-request-process)
- [Issue Reporting](#issue-reporting)
- [Areas for Contribution](#areas-for-contribution)

## Code of Conduct

### Our Pledge
We are committed to providing a welcoming and inclusive experience for everyone, regardless of background, experience level, gender identity, sexual orientation, disability, personal appearance, body size, race, ethnicity, age, religion, or nationality.

### Standards
- Use welcoming and inclusive language
- Be respectful of differing viewpoints and experiences
- Gracefully accept constructive criticism
- Focus on what is best for the community
- Show empathy towards other community members

### Enforcement
Instances of unacceptable behavior may be reported to the project maintainers. All complaints will be reviewed and investigated promptly and fairly.

## Getting Started

### Prerequisites
- **Android Studio**: Arctic Fox (2020.3.1) or later
- **JDK**: Version 8 or higher
- **Git**: Latest version
- **Android SDK**: API Level 34
- **Device/Emulator**: Android 7.0+ for testing

### Development Setup
1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/ColoringApp.git
   cd ColoringApp
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/Amit-JoYo/ColoringApp.git
   ```
4. **Open in Android Studio** and sync the project
5. **Run the app** to ensure everything works

### Environment Configuration
```bash
# Set up git hooks (optional but recommended)
cp .githooks/pre-commit .git/hooks/
chmod +x .git/hooks/pre-commit

# Install ktlint for code formatting
./gradlew ktlintCheck
```

## Development Workflow

### Branch Strategy
- **main**: Stable release branch
- **develop**: Integration branch for features
- **feature/**: New features (`feature/brush-tools`)
- **bugfix/**: Bug fixes (`bugfix/memory-leak`)
- **hotfix/**: Critical production fixes

### Workflow Steps
1. **Sync with upstream**:
   ```bash
   git checkout main
   git pull upstream main
   ```

2. **Create feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make changes** following code standards
4. **Commit regularly** with descriptive messages
5. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

6. **Create Pull Request** against `develop` branch

### Commit Message Format
Follow conventional commits format:
```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Build process or auxiliary tool changes

**Examples:**
```bash
feat(canvas): add brush size selection
fix(memory): resolve bitmap memory leak
docs(api): update PaintingViewModel documentation
```

## Code Standards

### Kotlin Style Guide
Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// ✅ Good
class PaintingViewModel : ViewModel() {
    private val _imageBitmap = MutableStateFlow<Bitmap?>(null)
    val imageBitmap = _imageBitmap.asStateFlow()
    
    fun setImageBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            _imageBitmap.value = processImage(bitmap)
        }
    }
}

// ❌ Bad
class paintingViewModel:ViewModel(){
    var imageBitmap:Bitmap?=null
    fun setImageBitmap(bitmap:Bitmap){
        imageBitmap=bitmap
    }
}
```

### Architecture Guidelines

#### MVVM Pattern
- **Views**: Only UI logic, no business logic
- **ViewModels**: State management and business logic
- **Models**: Data classes and utility functions

```kotlin
// ✅ Good - Separated concerns
@Composable
fun PaintingScreen(viewModel: PaintingViewModel) {
    val imageBitmap by viewModel.imageBitmap.collectAsState()
    // UI only
}

class PaintingViewModel : ViewModel() {
    // Business logic only
    fun processImage(bitmap: Bitmap) { ... }
}
```

#### State Management
- Use `StateFlow` for reactive state
- Prefer immutable data classes
- Handle loading and error states

```kotlin
// ✅ Good
data class UiState(
    val isLoading: Boolean = false,
    val imageBitmap: Bitmap? = null,
    val errorMessage: String? = null
)

private val _uiState = MutableStateFlow(UiState())
val uiState = _uiState.asStateFlow()
```

### Code Organization

#### File Structure
```
app/src/main/java/com/example/coloringapp/
├── ui/
│   ├── screens/          # Screen composables
│   ├── components/       # Reusable UI components
│   └── theme/           # Design system
├── domain/
│   ├── models/          # Data classes
│   ├── repositories/    # Data access interfaces
│   └── usecases/        # Business logic
├── data/
│   ├── repositories/    # Repository implementations
│   └── sources/         # Data sources
└── utils/               # Utility functions
```

#### Naming Conventions
- **Classes**: PascalCase (`PaintingViewModel`)
- **Functions**: camelCase (`setImageBitmap`)
- **Variables**: camelCase (`imageBitmap`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_IMAGE_SIZE`)
- **Composables**: PascalCase (`PaintingScreen`)

### Documentation Standards

#### KDoc Comments
Document all public APIs:
```kotlin
/**
 * Processes an image for coloring by applying K-means segmentation.
 * 
 * @param bitmap The input image to process
 * @param clusters Number of color clusters (default: 16)
 * @return Processed bitmap ready for coloring
 * @throws IllegalArgumentException if bitmap is null or invalid
 */
fun segmentImageByColor(
    bitmap: Bitmap, 
    clusters: Int = 16
): Bitmap
```

#### Inline Comments
Use sparingly for complex logic:
```kotlin
// Transform tap coordinates to bitmap coordinate system
val transformedOffset = (tapPoint - canvasCenter - offset) / scale + canvasCenter
```

## Testing Guidelines

### Test Categories

#### Unit Tests
Test individual functions and classes:
```kotlin
@Test
fun `segmentImageByColor should return original bitmap for grayscale images`() {
    // Given
    val grayscaleBitmap = createGrayscaleBitmap()
    
    // When
    val result = segmentImageByColor(grayscaleBitmap)
    
    // Then
    assertEquals(grayscaleBitmap, result)
}
```

#### Integration Tests
Test component interactions:
```kotlin
@Test
fun `PaintingViewModel should update imageBitmap when setImageBitmap is called`() = runTest {
    // Given
    val viewModel = PaintingViewModel()
    val testBitmap = createTestBitmap()
    
    // When
    viewModel.setImageBitmap(testBitmap)
    
    // Then
    viewModel.imageBitmap.test {
        assertNotNull(awaitItem())
    }
}
```

#### UI Tests
Test user interactions:
```kotlin
@Test
fun colorSelection_updatesSelectedColor() {
    composeTestRule.setContent {
        HoneycombColorPicker(onColorSelected = { })
    }
    
    composeTestRule
        .onNodeWithContentDescription("Red color")
        .performClick()
        
    // Assert color change
}
```

### Test Requirements
- **Coverage**: Aim for 80%+ code coverage
- **Naming**: Use descriptive test names
- **Structure**: Follow Given-When-Then pattern
- **Isolation**: Tests should not depend on each other
- **Speed**: Unit tests should run in < 100ms

### Running Tests
```bash
# Run all tests
./gradlew test

# Run unit tests only
./gradlew testDebugUnitTest

# Run instrumented tests
./gradlew connectedAndroidTest

# Generate coverage report
./gradlew jacocoTestReport
```

## Pull Request Process

### Before Submitting
- [ ] Code follows style guidelines
- [ ] All tests pass locally
- [ ] Documentation is updated
- [ ] Self-review completed
- [ ] Related issues are linked

### PR Description Template
```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing completed

## Screenshots (if applicable)
Before/after screenshots

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Tests pass locally
- [ ] Documentation updated
```

### Review Process
1. **Automated Checks**: CI/CD pipeline runs automatically
2. **Code Review**: At least one maintainer review required
3. **Testing**: Reviewer tests on different devices/scenarios
4. **Approval**: Maintainer approval before merge
5. **Merge**: Squash and merge to maintain clean history

### Review Criteria
- **Functionality**: Does it work as intended?
- **Code Quality**: Is it readable and maintainable?
- **Performance**: Does it impact app performance?
- **Security**: Are there any security concerns?
- **Design**: Does it follow app design patterns?

## Issue Reporting

### Bug Reports
Use the bug report template:
```markdown
**Describe the bug**
Clear description of the issue

**To Reproduce**
Steps to reproduce:
1. Go to '...'
2. Click on '....'
3. See error

**Expected behavior**
What should happen

**Screenshots**
If applicable

**Device Information**
- Device: [e.g. Samsung Galaxy S21]
- OS: [e.g. Android 12]
- App Version: [e.g. 1.0.0]

**Additional context**
Any other relevant information
```

### Feature Requests
Use the feature request template:
```markdown
**Is your feature request related to a problem?**
Description of the problem

**Describe the solution you'd like**
Clear description of desired feature

**Describe alternatives you've considered**
Alternative solutions considered

**Additional context**
Mockups, examples, or other context
```

### Performance Issues
Include performance metrics:
- Memory usage patterns
- CPU usage
- Battery drain information
- Device specifications

## Areas for Contribution

### High Priority
- **Performance Optimization**: Memory usage, processing speed
- **Accessibility**: Screen reader support, high contrast mode
- **Testing**: Increase test coverage, add integration tests
- **Documentation**: API docs, user guides, tutorials

### Medium Priority
- **New Features**: Brush tools, layer support, export options
- **UI/UX**: Animation improvements, better error handling
- **Internationalization**: Support for multiple languages
- **Tablet Support**: Optimized layouts for larger screens

### Low Priority
- **Advanced Features**: AI color suggestions, cloud sync
- **Developer Tools**: Debug utilities, performance monitoring
- **Platform Support**: Wear OS, Android TV versions

### Getting Started Ideas
Perfect for first-time contributors:
- Fix typos in documentation
- Add unit tests for existing functions
- Improve error messages
- Add accessibility labels
- Update dependencies

### Specialized Areas
For contributors with specific expertise:
- **Image Processing**: OpenCV optimizations, new algorithms
- **UI/UX Design**: Material Design improvements, animations
- **Performance**: Memory optimization, GPU acceleration
- **Accessibility**: WCAG compliance, assistive technology support

## Recognition

### Contributors
All contributors are recognized in:
- README.md contributors section
- Release notes acknowledgments
- GitHub contributors page

### Contribution Types
We value all types of contributions:
- 💻 Code contributions
- 📖 Documentation improvements
- 🐛 Bug reports and testing
- 💡 Feature suggestions
- 🎨 Design and UX improvements
- 🌍 Translations and localization

---

## Contact

- **GitHub Issues**: For bugs and feature requests
- **GitHub Discussions**: For questions and general discussion
- **Email**: amit.joyo@example.com for security issues
- **Discord**: ColoringApp Community Server

Thank you for contributing to ColoringApp! 🎨
