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

                    showSearchableDialog(prettyHeader, prettyPayload);

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

    private static void showSearchableDialog(String header, String content) {
        JDialog dialog = new JDialog((Frame) null, "Decoded JWT", true);
        dialog.setSize(850, 650); // Légèrement agrandi pour le confort visuel
        dialog.setLocationRelativeTo(null);

        // --- Définition d'une police élégante ---
        // Java va tester les polices dans l'ordre. Si "JetBrains Mono" ou "Consolas" ne sont pas installés,
        // il prendra une police à espacement fixe de haute qualité par défaut.
        Font elegantFont = new Font("JetBrains Mono", Font.PLAIN, 15);
        if (elegantFont.getName().equals("Dialog")) {
            elegantFont = new Font("Consolas", Font.PLAIN, 15);
        }
        if (elegantFont.getName().equals("Dialog")) {
            elegantFont = new Font("Monospaced", Font.PLAIN, 15);
        }

        // --- Configuration du Header ---
        JTextPane headerTextPane = new JTextPane();
        headerTextPane.setEditable(false);
        headerTextPane.setFont(elegantFont);
        headerTextPane.setOpaque(false);
        headerTextPane.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8)); // Marges internes pour respirer

        // Espacement des lignes (Interlignage à 1.2 pour l'élégance)
        MutableAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(set, 0.2f);
        headerTextPane.setParagraphAttributes(set, false);

        JScrollPane headerScrollPane = new JScrollPane(headerTextPane);
        headerScrollPane.setBorder(null);
        headerScrollPane.setOpaque(false);
        headerScrollPane.getViewport().setOpaque(false);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(headerScrollPane, BorderLayout.CENTER);
        headerPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Header",
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12))); // Titre du panel plus propre

        // --- Configuration du Payload ---
        JTextPane payloadTextPane = new JTextPane();
        payloadTextPane.setEditable(false);
        payloadTextPane.setFont(elegantFont);
        payloadTextPane.setOpaque(false);
        payloadTextPane.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        payloadTextPane.setParagraphAttributes(set, false); // Même interlignage

        JScrollPane payloadScrollPane = new JScrollPane(payloadTextPane);
        payloadScrollPane.setBorder(null);
        payloadScrollPane.setOpaque(false);
        payloadScrollPane.getViewport().setOpaque(false);

        JPanel payloadPanel = new JPanel(new BorderLayout());
        payloadPanel.add(payloadScrollPane, BorderLayout.CENTER);
        payloadPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Payload",
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12)));

        // Application de la coloration syntaxique
        applyJsonSyntaxHighlighting(headerTextPane, header);
        applyJsonSyntaxHighlighting(payloadTextPane, content);

        // JSplitPane pour organiser le tout
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, headerPanel, payloadPanel);
        splitPane.setDividerLocation(200); // Un peu plus grand pour la nouvelle taille de police
        splitPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        dialog.setLayout(new BorderLayout());
        dialog.add(splitPane, BorderLayout.CENTER);

        dialog.setAlwaysOnTop(true);
        dialog.toFront();
        dialog.requestFocus();
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