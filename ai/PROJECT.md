# Share to Archive.Today

## Project Overview
This is a React Native Expo iOS application that integrates with the iOS share menu to enable one-click archiving of web pages using Archive.today service.

## Core Functionality
1. Adds a custom share extension to iOS share menu
2. When user shares a URL from any app, our extension appears as an option
3. Upon selection, the app sends the shared URL to Archive.today
4. Uses Archive.today's URL scheme: `https://archive.today/?run=1&url=${encodedUrl}`

## Technical Requirements

### Mobile Platform
- iOS (primary target)
- Built with React Native + Expo

### Key Features
- iOS Share Extension integration
- URL handling and encoding
- Web request handling
- Minimal UI for confirmation/status

### Dependencies
- Expo
- React Native
- expo-sharing
- react-native-url-polyfill (for URL handling)

### Technical Considerations
1. Share Extension Setup
   - Must properly register as iOS share extension
   - Handle incoming shared URLs correctly

2. URL Processing
   - Properly encode shared URLs
   - Handle malformed URLs gracefully
   - Support various URL formats

3. Network Handling
   - Make HTTP requests to Archive.today
   - Handle network errors gracefully
   - Show loading/success/error states

4. User Experience
   - Quick and seamless archiving process
   - Clear feedback on archiving status
   - Minimal user interaction required

## Implementation Notes
- Use Expo's share extension capabilities
- Implement proper error handling
- Ensure URL encoding follows standards
- Keep UI minimal and focused 