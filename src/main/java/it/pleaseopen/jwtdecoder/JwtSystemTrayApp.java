package it.pleaseopen.jwtdecoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class JwtSystemTrayApp {

    public static void main(String[] args) {
        if (!SystemTray.isSupported()) {
            System.err.println("SystemTray not supported");
            return;
        }

        PopupMenu popup = new PopupMenu();
        MenuItem decodeItem = new MenuItem("Decode JWT from clipboard");
        MenuItem exitItem = new MenuItem("Exit");

        popup.add(decodeItem);
        popup.addSeparator();
        popup.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(createImage("icon.png", "JWT Decoder"), "JWT Decoder", popup);
        trayIcon.setImageAutoSize(true);

        SystemTray tray = SystemTray.getSystemTray();
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Unable to add tray icon");
            return;
        }

        decodeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String clipboardText = (String) Toolkit.getDefaultToolkit()
                            .getSystemClipboard().getData(DataFlavor.stringFlavor);

                    if(clipboardText.split("\\.").length != 3) {
                        throw new Exception("Invalid clipboard text");
                    }

                    byte[] headerBytes = Base64.getDecoder().decode(clipboardText.split("\\.")[0]);
                    byte[] payloadBytes = Base64.getDecoder().decode(clipboardText.split("\\.")[1]);

                    String decodedHeader = new String(headerBytes, StandardCharsets.UTF_8);
                    String decodedPayload = new String(payloadBytes, StandardCharsets.UTF_8);

                    // (pretty print)
                    ObjectMapper mapper = new ObjectMapper();
                    ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

                    String prettyHeader = writer.writeValueAsString(mapper.readTree(decodedHeader));
                    JsonNode payloadNode = mapper.readTree(decodedPayload);
                    ObjectNode payloadObject = (ObjectNode) payloadNode;

                    String dates = "";
                    if (payloadObject.has("iat") && payloadObject.get("iat").isNumber()) {
                        long iat = payloadObject.get("iat").asLong();
                        dates = dates + " iat : "+Instant.ofEpochSecond(iat).toString()+" \n";
                    }
                    if (payloadObject.has("exp") && payloadObject.get("exp").isNumber()) {
                        long exp = payloadObject.get("exp").asLong();
                        dates = dates + " exp : "+Instant.ofEpochSecond(exp).toString()+" \n";
                    }
                    String prettyPayload = writer.writeValueAsString(payloadObject);


                    String fullContent = "Header:\n" + prettyHeader + "\n\nPayload:\n"+ dates + "\n" + prettyPayload;

                    showSearchableDialog(fullContent);

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null,
                            "Unable to decode JWT.\n" + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        exitItem.addActionListener(e -> tray.remove(trayIcon));
    }

    protected static Image createImage(String path, String description) {
        // from https://mkyong.com/java/java-read-a-file-from-resources-folder/
        ClassLoader classLoader = JwtSystemTrayApp.class.getClassLoader();
        URL imageURL = classLoader.getResource(path);
        if (imageURL == null) {
            System.err.println("Resource not found: " + path);
            return null;
        } else {
            return (new ImageIcon(imageURL, description)).getImage();
        }
    }

    private static void showSearchableDialog(String content) {
        JDialog dialog = new JDialog((Frame) null, "Decoded JWT", true);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(null);

        JTextField searchField = new JTextField();
        JButton nextButton = new JButton("Find Next");
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(nextButton, BorderLayout.EAST);

        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textPane);

        applyJsonSyntaxHighlighting(textPane, content);

        Highlighter highlighter = textPane.getHighlighter();
        Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);

        final int[] currentIndex = { -1 };
        final java.util.List<Integer> occurrences = new java.util.ArrayList<>();

        Runnable searchAndHighlight = () -> {
            highlighter.removeAllHighlights();
            occurrences.clear();
            currentIndex[0] = -1;

            String text = textPane.getText().toLowerCase();
            String query = searchField.getText().toLowerCase();

            if (query.isEmpty()) return;

            int index = 0;
            while ((index = text.indexOf(query, index)) >= 0) {
                occurrences.add(index);
                try {
                    highlighter.addHighlight(index, index + query.length(), painter);
                } catch (BadLocationException ignored) {}
                index += query.length();
            }

            if (!occurrences.isEmpty()) {
                currentIndex[0] = 0;
                int start = occurrences.get(0);
                textPane.setCaretPosition(start);
                textPane.select(start, start + query.length());
                searchField.requestFocusInWindow();
            }
        };

        nextButton.addActionListener(e -> {
            if (occurrences.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "No occurrences found.");
                return;
            }
            currentIndex[0]++;
            if (currentIndex[0] >= occurrences.size()) {
                currentIndex[0] = 0;
            }
            int start = occurrences.get(currentIndex[0]);
            int end = start + searchField.getText().length();
            textPane.setCaretPosition(start);
            textPane.select(start, end);
            textPane.requestFocus();
        });

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                searchAndHighlight.run();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                searchAndHighlight.run();
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                searchAndHighlight.run();
            }
        });

        dialog.setLayout(new BorderLayout());
        dialog.add(topPanel, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);

        dialog.setVisible(true);
    }

    private static void applyJsonSyntaxHighlighting(JTextPane pane, String text) {
        StyledDocument doc = pane.getStyledDocument();

        // Styles
        StyleContext sc = StyleContext.getDefaultStyleContext();
        AttributeSet attrKey = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, new Color(0, 0, 255));
        AttributeSet attrString = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, new Color(163, 21, 21));
        AttributeSet attrNumber = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, new Color(43, 145, 175));
        AttributeSet attrBoolean = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, new Color(255, 140, 0));

        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, text, null);

            String lowerText = text.toLowerCase();
            int pos = 0;

            // JSON :
            // "key":
            java.util.regex.Pattern keyPattern = java.util.regex.Pattern.compile("\"(\\\\.|[^\"])*\"(?=\\s*:)");
            // String value : "value"
            java.util.regex.Pattern stringPattern = java.util.regex.Pattern.compile(":\\s*\"(\\\\.|[^\"])*\"");
            // Number value
            java.util.regex.Pattern numberPattern = java.util.regex.Pattern.compile(":\\s*(-?\\d+(\\.\\d+)?)");
            // Boolean or null
            java.util.regex.Pattern boolNullPattern = java.util.regex.Pattern.compile(":\\s*(true|false|null)");

            // Color keys
            java.util.regex.Matcher mKey = keyPattern.matcher(text);
            while (mKey.find()) {
                int start = mKey.start();
                int end = mKey.end();
                doc.setCharacterAttributes(start, end - start, attrKey, false);
            }

            // Color string values
            java.util.regex.Matcher mString = stringPattern.matcher(text);
            while (mString.find()) {
                int start = mString.start() + mString.group().indexOf("\"");
                int end = mString.end();
                doc.setCharacterAttributes(start, end - start, attrString, false);
            }

            // Color numbers
            java.util.regex.Matcher mNumber = numberPattern.matcher(text);
            while (mNumber.find()) {
                int start = mNumber.start() + mNumber.group().indexOf(mNumber.group(1));
                int end = mNumber.end();
                doc.setCharacterAttributes(start, end - start, attrNumber, false);
            }

            // Color booleans and null
            java.util.regex.Matcher mBool = boolNullPattern.matcher(lowerText);
            while (mBool.find()) {
                int start = mBool.start() + mBool.group().indexOf(mBool.group(1));
                int end = mBool.end();
                doc.setCharacterAttributes(start, end - start, attrBoolean, false);
            }

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

}