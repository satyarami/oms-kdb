package com.satya.oms.ui;

import com.satya.oms.aeron.AeronService;
import com.satya.oms.model.OrderRecord;
import com.satya.oms.model.OrderStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Order-entry form.  User fills in Symbol, Side, Quantity, Price and clicks Send.
 * The panel encodes an SBE message and publishes it on Aeron stream 1001.
 * It also registers the pending order in the OrderStore immediately so the
 * blotter can show it before the execution report arrives.
 */
public class OrderEntryPanel extends JPanel {

    // ---- known symbols (symbolId = index + 1) ----
    private static final String[] SYMBOLS = {"AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "NVDA"};

    private static final AtomicLong ORDER_ID_SEQ = new AtomicLong(
            System.currentTimeMillis() & 0x0FFF_FFFFL);

    private final AeronService aeron;
    private final OrderStore   store;

    // form components
    private final JComboBox<String> symbolCombo   = new JComboBox<>(SYMBOLS);
    private final JComboBox<String> sideCombo     = new JComboBox<>(new String[]{"BUY", "SELL"});
    private final JTextField        quantityField = new JTextField("100", 10);
    private final JTextField        priceField    = new JTextField("100.00", 10);
    private final JButton           sendButton    = new JButton("Send Order");
    private final JLabel            statusLabel   = new JLabel(" ");

    public OrderEntryPanel(AeronService aeron, OrderStore store) {
        this.aeron = aeron;
        this.store = store;
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(20, 30, 20, 30));

        // ---- title ----
        JLabel title = new JLabel("New Order Entry");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setBorder(new EmptyBorder(0, 0, 16, 0));
        add(title, BorderLayout.NORTH);

        // ---- form grid ----
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints lc = labelConstraints();
        GridBagConstraints fc = fieldConstraints();

        int row = 0;
        addRow(form, "Symbol:",   symbolCombo,   lc, fc, row++);
        addRow(form, "Side:",     sideCombo,     lc, fc, row++);
        addRow(form, "Quantity:", quantityField, lc, fc, row++);
        addRow(form, "Price:",    priceField,    lc, fc, row++);

        // send button spans both columns
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0; bc.gridy = row; bc.gridwidth = 2;
        bc.insets = new Insets(16, 0, 4, 0);
        bc.fill = GridBagConstraints.HORIZONTAL;
        sendButton.setFont(sendButton.getFont().deriveFont(Font.BOLD, 13f));
        sendButton.setBackground(new Color(0, 120, 215));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        form.add(sendButton, bc);

        // status label
        bc = new GridBagConstraints();
        bc.gridx = 0; bc.gridy = row + 1; bc.gridwidth = 2;
        bc.insets = new Insets(4, 0, 0, 0);
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        form.add(statusLabel, bc);

        // wrap form so it doesn't stretch
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wrapper.setOpaque(false);
        wrapper.add(form);
        add(wrapper, BorderLayout.CENTER);

        sendButton.addActionListener(e -> onSend());
    }

    // ------------------------------------------------------------------
    // Send logic
    // ------------------------------------------------------------------
    private void onSend() {
        String validationError = validateInput();
        if (validationError != null) {
            showStatus(validationError, true);
            return;
        }

        String  symbol   = (String) symbolCombo.getSelectedItem();
        byte    side     = sideCombo.getSelectedIndex() == 0 ? (byte) 0 : (byte) 1; // BUY=0 SELL=1
        long    qty      = Long.parseLong(quantityField.getText().trim());
        long    priceTix = Math.round(Double.parseDouble(priceField.getText().trim()) * 100);
        long    orderId  = ORDER_ID_SEQ.incrementAndGet();
        long    symbolId = symbolIndex(symbol) + 1;

        // Register in store immediately (before execution arrives)
        OrderRecord.Side recordSide = side == 0 ? OrderRecord.Side.BUY : OrderRecord.Side.SELL;
        OrderRecord rec = new OrderRecord(orderId, symbol, recordSide, qty, priceTix);
        store.addOrder(rec);

        // Publish over Aeron
        boolean ok = aeron.sendOrder(orderId, symbolId, side, qty, priceTix);
        if (ok) {
            showStatus("✓ Order #" + orderId + " submitted — " + symbol + " " +
                    sideCombo.getSelectedItem() + " " + qty + " @ " + priceField.getText().trim(), false);
        } else {
            showStatus("✗ Send failed — is the OMS running?", true);
        }
    }

    private String validateInput() {
        try {
            long qty = Long.parseLong(quantityField.getText().trim());
            if (qty <= 0) return "Quantity must be > 0";
        } catch (NumberFormatException e) {
            return "Quantity must be a whole number";
        }
        try {
            double px = Double.parseDouble(priceField.getText().trim());
            if (px <= 0) return "Price must be > 0";
        } catch (NumberFormatException e) {
            return "Price must be a number (e.g. 123.45)";
        }
        return null;
    }

    private void showStatus(String msg, boolean error) {
        statusLabel.setText(msg);
        statusLabel.setForeground(error ? new Color(200, 50, 50) : new Color(0, 150, 0));
    }

    private int symbolIndex(String sym) {
        for (int i = 0; i < SYMBOLS.length; i++) if (SYMBOLS[i].equals(sym)) return i;
        return 0;
    }

    // ---- GridBag helpers ----
    private static GridBagConstraints labelConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.LINE_END;
        c.insets = new Insets(6, 0, 6, 12);
        return c;
    }

    private static GridBagConstraints fieldConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        c.insets = new Insets(6, 0, 6, 0);
        return c;
    }

    private static void addRow(JPanel p, String labelText, JComponent field,
                                GridBagConstraints lc, GridBagConstraints fc, int row) {
        lc.gridy = row; fc.gridy = row;
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(13f));
        p.add(label, lc);
        p.add(field,  fc);
    }
}
