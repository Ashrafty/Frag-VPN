# Contributing to Frag VPN

Thank you for considering contributing to Frag VPN! This document provides guidelines and instructions for contributing to this project.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for everyone.

## How Can I Contribute?

### Reporting Bugs

- Check if the bug has already been reported in the Issues section
- Use the bug report template when creating a new issue
- Include detailed steps to reproduce the bug
- Provide information about your environment (OS, Flutter version, etc.)
- Include screenshots if applicable

### Suggesting Features

- Check if the feature has already been suggested in the Issues section
- Use the feature request template when creating a new issue
- Clearly describe the feature and its benefits
- Consider how the feature fits into the existing architecture

### Code Contributions

1. Fork the repository
2. Create a new branch for your feature or bugfix
3. Write your code following the coding standards
4. Add tests for your changes
5. Ensure all tests pass
6. Submit a pull request

## Development Setup

1. Install Flutter SDK (version 3.7.2 or higher)
2. Clone your fork of the repository
3. Run `flutter pub get` to install dependencies
4. Run `flutter test` to ensure everything is working correctly

## Coding Standards

- Follow the [Dart style guide](https://dart.dev/guides/language/effective-dart/style)
- Write meaningful commit messages
- Document your code with comments
- Keep functions small and focused on a single task
- Use descriptive variable and function names

## Pull Request Process

1. Update the README.md with details of changes if applicable
2. Update the documentation if you're changing functionality
3. The PR should work on Android and iOS platforms
4. Link any related issues in your PR description
5. Wait for review from maintainers

## Adding a New Language

1. Create a new JSON file in the `assets/lang/` directory (e.g., `de.json`)
2. Copy the structure from an existing language file (e.g., `en.json`)
3. Translate all the strings
4. Add the new locale to the supported locales list in `main.dart`
5. Test the app with the new language

## Testing

- Write unit tests for new functionality
- Test your changes on both Android and iOS if possible
- Ensure your changes don't break existing functionality

## Questions?

If you have any questions about contributing, feel free to open an issue asking for clarification.

Thank you for contributing to Frag VPN!
