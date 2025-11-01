import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Extension implements BurpExtension {
    private MontoyaApi api;
    private List<HttpRequestResponse> repeaterHistory;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        this.api = montoyaApi;
        this.repeaterHistory = new ArrayList<>();

        // Set extension name
        api.extension().setName("Repeater History Copy");

        // Register context menu provider
        api.userInterface().registerContextMenuItemsProvider(new RepeaterHistoryContextMenuProvider());

        // Register HTTP handler to capture Repeater requests/responses
        api.http().registerHttpHandler(new RepeaterHttpHandler());

        api.logging().logToOutput("Repeater History Copy extension loaded successfully");
    }

    /**
     * HTTP Handler to capture Repeater traffic
     */
    private class RepeaterHttpHandler implements burp.api.montoya.http.handler.HttpHandler {
        @Override
        public burp.api.montoya.http.handler.RequestToBeSentAction handleHttpRequestToBeSent(
                burp.api.montoya.http.handler.HttpRequestToBeSent requestToBeSent) {
            // Capture requests sent from Repeater
            if (requestToBeSent.toolSource().isFromTool(burp.api.montoya.core.ToolType.REPEATER)) {
                // We'll store this when we get the response
            }
            return burp.api.montoya.http.handler.RequestToBeSentAction.continueWith(requestToBeSent);
        }

        @Override
        public burp.api.montoya.http.handler.ResponseReceivedAction handleHttpResponseReceived(
                burp.api.montoya.http.handler.HttpResponseReceived responseReceived) {
            // Capture request/response pairs from Repeater
            if (responseReceived.toolSource().isFromTool(burp.api.montoya.core.ToolType.REPEATER)) {
                synchronized (repeaterHistory) {
                    repeaterHistory.add(HttpRequestResponse.httpRequestResponse(
                        responseReceived.initiatingRequest(),
                        responseReceived
                    ));
                }
            }
            return burp.api.montoya.http.handler.ResponseReceivedAction.continueWith(responseReceived);
        }
    }

    /**
     * Context menu provider that adds "Copy All Repeater History" option
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

                JMenuItem copyAllHistory = new JMenuItem("Copy All Captured History");
                copyAllHistory.addActionListener(e -> copyRepeaterHistory(event));
                menuItems.add(copyAllHistory);
            }

            return menuItems;
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
     * Retrieves and copies all captured Repeater history to clipboard
     */
    private void copyRepeaterHistory(ContextMenuEvent event) {
        try {
            List<HttpRequestResponse> history;
            synchronized (repeaterHistory) {
                history = new ArrayList<>(repeaterHistory);
            }

            if (history.isEmpty()) {
                api.logging().logToOutput("No captured Repeater history found");
                showNotification("No captured history available.\n\nThis extension captures requests sent AFTER it was loaded.\nUse 'Copy Current Request/Response' to copy what's visible now.");
                return;
            }

            // Format the history
            String formattedHistory = formatHistory(history);

            // Copy to clipboard
            copyToClipboard(formattedHistory);

            api.logging().logToOutput("Successfully copied " + history.size() + " captured history entries to clipboard");
            showNotification("Copied " + history.size() + " captured history entries to clipboard");

        } catch (Exception e) {
            api.logging().logToError("Error copying Repeater history: " + e.getMessage());
            e.printStackTrace();
            showNotification("Error copying history: " + e.getMessage());
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
     * Formats the history entries into a readable string
     */
    private String formatHistory(List<HttpRequestResponse> history) {
        StringBuilder sb = new StringBuilder();
        String separator = "=".repeat(80);

        sb.append("BURP REPEATER HISTORY\n");
        sb.append(separator).append("\n");
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