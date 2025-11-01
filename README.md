# Repeater History Copy

A Burp Suite extension that allows you to copy request and response history from the Repeater tool with a single click.

## Contents
* [Features](#features)
* [Installation](#installation)
* [Usage](#usage)
* [How it works](#how-it-works)
* [Building from source](#building-from-source)
* [Technical details](#technical-details)


## Features

- **Copy Current Request/Response**: Right-click in Repeater to copy the currently visible request and response to clipboard
- **Copy Captured History**: Capture and copy all requests sent through Repeater after the extension is loaded
- **Formatted Output**: History is formatted with clear separators and numbering for easy reading
- **Quick Access**: Single-click context menu integration with Repeater
- **Thread-Safe**: Properly handles concurrent requests from Repeater

## Installation

1. Download the pre-built JAR file from the releases, or build from source (see below)
2. In Burp Suite, go to **Extensions > Installed**
3. Click **Add**
4. Select the JAR file and click **Open**
5. The extension is now loaded and ready to use

## Usage

### Copying Current Request/Response

1. In Burp Repeater, send any request or navigate to a previous entry using the back/forward buttons
2. Right-click in the request or response panel
3. Select **"Copy Current Request/Response"**
4. The formatted request and response are now on your clipboard
5. Paste anywhere you need the data (text editor, notes, reports, etc.)

### Copying All Captured History

1. Send multiple requests in Repeater (after loading the extension)
2. Right-click in any Repeater message editor
3. Select **"Copy All Captured History"**
4. All captured requests and responses are copied to clipboard in formatted form

### Output Format

```
CURRENT REQUEST/RESPONSE
================================================================================

REQUEST
================================================================================
[Full HTTP request with headers and body]

--------------------------------------------------------------------------------
RESPONSE
--------------------------------------------------------------------------------
[Full HTTP response with headers and body]

================================================================================
```

## How it Works

The extension:
1. Registers a context menu provider with the Montoya API
2. Appears whenever you right-click in a Repeater message editor
3. Uses an HTTP handler to capture requests/responses sent through Repeater
4. Formats captured data into readable text
5. Copies formatted text to system clipboard using Java's clipboard API

**Important Note**: Due to Montoya API limitations, the extension cannot access Repeater's built-in per-tab history navigation (back/forward buttons). Instead, use "Copy Current Request/Response" to copy whatever is currently visible, then navigate manually if needed.

---

## Building from Source

### Prerequisites

- JDK 21 or higher
- Gradle (included via wrapper)
- Burp Suite Community or Professional

### Build Steps

1. Clone or download this repository
2. Navigate to the project directory
3. Run the build command:
   - **Linux/macOS**: `./gradlew jar`
   - **Windows**: `gradlew jar`

4. The JAR file will be created at: `build/libs/extension-template-project.jar`

### Loading into Burp

1. Open Burp Suite
2. Go to **Extensions > Installed**
3. Click **Add**
4. Select the JAR file from `build/libs/`
5. Click **Next** and then **Close**

### Quick Reload During Development

After making code changes, rebuild with `./gradlew jar`, then in Burp:
1. Go to **Extensions > Installed**
2. Hold `Ctrl` (Windows/Linux) or `⌘` (macOS)
3. Click the **Loaded** checkbox next to the extension

## Technical Details

### Architecture

- **Main Class**: `Extension.java` - Implements `BurpExtension` interface
- **Context Menu Provider**: Registers menu items in Repeater message editors
- **HTTP Handler**: Captures requests/responses from the Repeater tool
- **Clipboard Integration**: Uses Java's `Toolkit.getDefaultToolkit().getSystemClipboard()`

### Thread Safety

- Request/response history is stored in a thread-safe `ArrayList` protected by synchronization
- HTTP handler runs in Burp's threading model and is properly synchronized

### API Compatibility

- **Montoya API Version**: 2025.5+
- **Java Version**: 21+
- **Burp Suite**: Community and Professional editions

### Known Limitations

- Cannot access Repeater's built-in history navigation (back/forward buttons) - these are internal UI components not exposed by the Montoya API
- Only captures new requests after the extension is loaded
- History is stored in memory and is cleared when Burp closes

### Code Structure

```
Extension.java
├── initialize()          - Entry point, sets up context menu and HTTP handler
├── RepeaterHttpHandler   - Inner class that captures HTTP traffic from Repeater
├── RepeaterHistoryContextMenuProvider - Inner class for context menu integration
├── copyCurrentRequestResponse()       - Copies visible request/response
├── copyRepeaterHistory()              - Copies all captured history
├── formatSingleItem()                 - Formats a single request/response pair
├── formatHistory()                    - Formats multiple request/response pairs
├── copyToClipboard()                  - System clipboard integration
└── showNotification()                 - User feedback dialog
```

## License

This extension is provided as-is for use with Burp Suite.