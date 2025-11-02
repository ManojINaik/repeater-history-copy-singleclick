import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKeyContext;
import burp.api.montoya.ui.hotkey.HotKeyEvent;
import burp.api.montoya.ui.hotkey.HotKeyHandler;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Extension implements BurpExtension {
    private MontoyaApi api;
    private Map<String, List<HttpRequestResponse>> tabHistory;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        this.api = montoyaApi;
        this.tabHistory = new ConcurrentHashMap<>();

        // Set extension name
        api.extension().setName("Repeater History Copy");

        // Register context menu provider
        api.userInterface().registerContextMenuItemsProvider(new RepeaterHistoryContextMenuProvider());

        // Register HTTP handler to capture Repeater requests/responses
        api.http().registerHttpHandler(new RepeaterHttpHandler());

        // Register hotkey Ctrl+Alt+C for copying tab history
        api.userInterface().registerHotKeyHandler(
            HotKeyContext.HTTP_MESSAGE_EDITOR,
            "ctrl alt C",
            new TabHistoryHotKeyHandler()
        );

        api.logging().logToOutput("Repeater History Copy extension loaded successfully");
        api.logging().logToOutput("Hotkey registered: Ctrl+Alt+C to copy tab history");
    }

    /**
     * HTTP Handler to capture Repeater traffic per tab
     */
    private class RepeaterHttpHandler implements burp.api.montoya.http.handler.HttpHandler {
        @Override
        public burp.api.montoya.http.handler.RequestToBeSentAction handleHttpRequestToBeSent(
                burp.api.montoya.http.handler.HttpRequestToBeSent requestToBeSent) {
            // We'll capture when we get the response
            return burp.api.montoya.http.handler.RequestToBeSentAction.continueWith(requestToBeSent);
        }

        @Override
        public burp.api.montoya.http.handler.ResponseReceivedAction handleHttpResponseReceived(
                burp.api.montoya.http.handler.HttpResponseReceived responseReceived) {
            // Capture request/response pairs from Repeater
            if (responseReceived.toolSource().isFromTool(burp.api.montoya.core.ToolType.REPEATER)) {
                // Use the target URL as the tab identifier
                String tabKey = getTabKey(responseReceived.initiatingRequest());

                HttpRequestResponse reqResp = HttpRequestResponse.httpRequestResponse(
                    responseReceived.initiatingRequest(),
                    responseReceived
                );

                List<HttpRequestResponse> history = tabHistory.computeIfAbsent(tabKey, k -> Collections.synchronizedList(new ArrayList<>()));
                history.add(reqResp);

                api.logging().logToOutput(">>> CAPTURED request to tab: " + tabKey + " | Total entries in this tab: " + history.size());
            }
            return burp.api.montoya.http.handler.ResponseReceivedAction.continueWith(responseReceived);
        }
    }

    /**
     * Generates a unique key for a Repeater tab based on the request URL
     */
    private String getTabKey(HttpRequest request) {
        return request.httpService().host() + ":" + request.httpService().port();
    }

    /**
     * Context menu provider that adds "Copy Repeater Tab History" options
     */
    private class RepeaterHistoryContextMenuProvider implements ContextMenuItemsProvider {
        @Override
        public List<Component> provideMenuItems(ContextMenuEvent event) {
            List<Component> menuItems = new ArrayList<>();

            // Show menu item in message editor contexts (Repeater uses MESSAGE_EDITOR)
            Optional<MessageEditorHttpRequestResponse> messageEditor = event.messageEditorRequestResponse();
            if (messageEditor.isPresent()) {
                JMenuItem copyCurrentItem = new JMenuItem("Copy Current Request/Response");
                copyCurrentItem.addActionListener(e -> copyCurrentRequestResponse(event));
                menuItems.add(copyCurrentItem);

                JMenuItem copyTabHistory = new JMenuItem("Copy This Tab's History");
                copyTabHistory.addActionListener(e -> copyCurrentTabHistory(event));
                menuItems.add(copyTabHistory);
            }

            return menuItems;
        }
    }

    /**
     * Hotkey handler for Ctrl+Alt+C to copy tab history
     */
    private class TabHistoryHotKeyHandler implements HotKeyHandler {
        @Override
        public void handle(HotKeyEvent event) {
            api.logging().logToOutput("=== HOTKEY CTRL+ALT+C TRIGGERED ===");

            try {
                // Get the current request/response from the message editor
                Optional<MessageEditorHttpRequestResponse> messageEditor = event.messageEditorRequestResponse();
                if (messageEditor.isEmpty() || messageEditor.get().requestResponse() == null) {
                    api.logging().logToOutput("Hotkey triggered but no request/response data available");
                    showNotification("No request/response data available");
                    return;
                }

                HttpRequest currentRequest = messageEditor.get().requestResponse().request();
                if (currentRequest == null) {
                    api.logging().logToOutput("Hotkey triggered but no request data available");
                    showNotification("No request data available");
                    return;
                }

                // Get the tab key for the current tab
                String tabKey = getTabKey(currentRequest);
                api.logging().logToOutput("Identified tab key: " + tabKey);

                // Get history for this specific tab
                List<HttpRequestResponse> history = tabHistory.get(tabKey);

                if (history == null) {
                    api.logging().logToOutput("History is NULL for tab: " + tabKey);
                    api.logging().logToOutput("All tab keys in history: " + tabHistory.keySet());
                } else {
                    api.logging().logToOutput("History size for tab " + tabKey + ": " + history.size());
                }

                if (history == null || history.isEmpty()) {
                    api.logging().logToOutput("Hotkey triggered - No captured history for tab: " + tabKey);
                    showNotification("No captured history for this tab.\n\nTab: " + tabKey + "\n\nThis extension captures requests sent AFTER it was loaded.\n\nSend some requests in Repeater first!");
                    return;
                }

                // Create a copy to avoid concurrent modification
                List<HttpRequestResponse> historyCopy;
                synchronized (history) {
                    historyCopy = new ArrayList<>(history);
                }

                api.logging().logToOutput("About to format " + historyCopy.size() + " history entries");

                // Format the history
                String formattedHistory = formatHistory(historyCopy, tabKey);

                api.logging().logToOutput("Formatted history length: " + formattedHistory.length() + " characters");
                api.logging().logToOutput("First 200 chars: " + formattedHistory.substring(0, Math.min(200, formattedHistory.length())));

                // Copy to clipboard
                copyToClipboard(formattedHistory);

                api.logging().logToOutput("Hotkey: Successfully copied " + historyCopy.size() + " history entries from tab: " + tabKey);
                api.logging().logToOutput("=== HOTKEY COPY COMPLETE ===");
                showNotification("✓ Copied " + historyCopy.size() + " HISTORY ENTRIES from this tab\n\nTab: " + tabKey + "\n\nUsing hotkey: Ctrl+Alt+C");

            } catch (Exception e) {
                api.logging().logToError("Error handling hotkey: " + e.getMessage());
                e.printStackTrace();
                showNotification("Error copying tab history: " + e.getMessage());
            }
        }
    }

    /**
     * Copies the currently visible request/response to clipboard
     */
    private void copyCurrentRequestResponse(ContextMenuEvent event) {
        try {
            Optional<MessageEditorHttpRequestResponse> messageEditor = event.messageEditorRequestResponse();
            if (messageEditor.isEmpty()) {
                showNotification("No request/response data available");
                return;
            }

            HttpRequestResponse currentItem = messageEditor.get().requestResponse();

            if (currentItem == null || currentItem.request() == null) {
                showNotification("No request data available");
                return;
            }

            // Format the current item
            String formatted = formatSingleItem(currentItem, "CURRENT");

            // Copy to clipboard
            copyToClipboard(formatted);

            api.logging().logToOutput("Successfully copied current request/response to clipboard");
            showNotification("Copied current request/response to clipboard");

        } catch (Exception e) {
            api.logging().logToError("Error copying current request/response: " + e.getMessage());
            e.printStackTrace();
            showNotification("Error: " + e.getMessage());
        }
    }

    /**
     * Retrieves and copies history from the current Repeater tab to clipboard
     */
    private void copyCurrentTabHistory(ContextMenuEvent event) {
        try {
            // Get the current request to identify which tab we're in
            Optional<MessageEditorHttpRequestResponse> messageEditor = event.messageEditorRequestResponse();
            if (messageEditor.isEmpty() || messageEditor.get().requestResponse() == null) {
                showNotification("No request data available to identify tab");
                return;
            }

            HttpRequest currentRequest = messageEditor.get().requestResponse().request();
            if (currentRequest == null) {
                showNotification("No request data available to identify tab");
                return;
            }

            // Get the tab key for the current tab
            String tabKey = getTabKey(currentRequest);

            // Get history for this specific tab
            List<HttpRequestResponse> history = tabHistory.get(tabKey);

            if (history == null || history.isEmpty()) {
                api.logging().logToOutput("No captured history for this tab: " + tabKey);
                showNotification("No captured history for this tab.\n\nTab: " + tabKey + "\n\nThis extension captures requests sent AFTER it was loaded.\nUse 'Copy Current Request/Response' to copy what's visible now.");
                return;
            }

            // Create a copy to avoid concurrent modification
            List<HttpRequestResponse> historyCopy;
            synchronized (history) {
                historyCopy = new ArrayList<>(history);
            }

            // Format the history
            String formattedHistory = formatHistory(historyCopy, tabKey);

            // Copy to clipboard
            copyToClipboard(formattedHistory);

            api.logging().logToOutput("Successfully copied " + historyCopy.size() + " history entries from tab: " + tabKey);
            showNotification("Copied " + historyCopy.size() + " history entries from this tab\n(" + tabKey + ")");

        } catch (Exception e) {
            api.logging().logToError("Error copying tab history: " + e.getMessage());
            e.printStackTrace();
            showNotification("Error copying tab history: " + e.getMessage());
        }
    }

    /**
     * Formats a single request/response item
     */
    private String formatSingleItem(HttpRequestResponse item, String label) {
        StringBuilder sb = new StringBuilder();
        String separator = "=".repeat(80);

        sb.append(label).append(" REQUEST/RESPONSE\n");
        sb.append(separator).append("\n\n");

        // Request
        sb.append("REQUEST\n");
        sb.append(separator).append("\n");
        HttpRequest request = item.request();
        if (request != null) {
            sb.append(request.toString()).append("\n");
        } else {
            sb.append("[No request data]\n");
        }

        sb.append("\n").append("-".repeat(80)).append("\n");
        sb.append("RESPONSE\n");
        sb.append("-".repeat(80)).append("\n");

        // Response
        HttpResponse response = item.response();
        if (response != null) {
            sb.append(response.toString()).append("\n");
        } else {
            sb.append("[No response data]\n");
        }

        sb.append("\n").append(separator).append("\n");

        return sb.toString();
    }

    /**
     * Formats the history entries into a readable string with tab information
     */
    private String formatHistory(List<HttpRequestResponse> history, String tabKey) {
        StringBuilder sb = new StringBuilder();
        String separator = "=".repeat(80);

        sb.append("BURP REPEATER TAB HISTORY\n");
        sb.append(separator).append("\n");
        sb.append("Tab: ").append(tabKey).append("\n");
        sb.append("Total Entries: ").append(history.size()).append("\n");
        sb.append(separator).append("\n\n");

        for (int i = 0; i < history.size(); i++) {
            HttpRequestResponse item = history.get(i);

            // Entry header
            sb.append("REQUEST #").append(i + 1).append("\n");
            sb.append(separator).append("\n");

            // Request
            HttpRequest request = item.request();
            if (request != null) {
                sb.append(request.toString()).append("\n");
            } else {
                sb.append("[No request data]\n");
            }

            sb.append("\n").append("-".repeat(80)).append("\n");
            sb.append("RESPONSE #").append(i + 1).append("\n");
            sb.append("-".repeat(80)).append("\n");

            // Response
            HttpResponse response = item.response();
            if (response != null) {
                sb.append(response.toString()).append("\n");
            } else {
                sb.append("[No response data]\n");
            }

            sb.append("\n").append(separator).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * Copies the formatted text to system clipboard
     */
    private void copyToClipboard(String text) {
        try {
            StringSelection stringSelection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        } catch (Exception e) {
            api.logging().logToError("Failed to copy to clipboard: " + e.getMessage());
            throw new RuntimeException("Clipboard operation failed", e);
        }
    }

    /**
     * Shows a notification dialog to the user
     */
    private void showNotification(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                null,
                message,
                "Repeater History Copy",
                JOptionPane.INFORMATION_MESSAGE
            );
        });
    }
}