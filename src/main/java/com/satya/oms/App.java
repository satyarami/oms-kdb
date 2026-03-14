package com.satya.oms;

import com.formdev.flatlaf.FlatDarkLaf;
import com.satya.oms.aeron.AeronService;
import com.satya.oms.model.OrderStore;
import com.satya.oms.ui.OrderBlotterPanel;
import com.satya.oms.ui.OrderEntryPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * OMS UI — independent client.
 * Connects to Aeron IPC (stream 1001 = orders out, stream 1002 = executions in).
 * Presents two tabs:
 *   1) Order Entry  — user fills in order fields and hits Send
 *   2) Order Blotter — live table of orders with fill rows indented below each order
 */
public class App {

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        UIManager.put("TabbedPane.selectedBackground", new Color(50, 65, 90));
        SwingUtilities.invokeLater(App::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        OrderStore store = new OrderStore();

        AeronService aeron;
        try {
            aeron = new AeronService(store);
            aeron.startSubscriber();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                    "Could not connect to Aeron Media Driver.\n" +
                    "Make sure the OMS Media Driver is running.\n\n" +
                    ex.getMessage(),
                    "Aeron Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        OrderEntryPanel   entryPanel   = new OrderEntryPanel(aeron, store);
        OrderBlotterPanel blotterPanel = new OrderBlotterPanel(store);

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 13f));
        tabs.addTab("  Order Entry",   entryPanel);
        tabs.addTab("  Order Blotter", blotterPanel);
        tabs.setPreferredSize(new Dimension(960, 640));

        JPanel statusBar = buildStatusBar(aeron);

        JFrame frame = new JFrame("OMS Client");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(tabs,      BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        final AeronService aeronRef = aeron;
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                int choice = JOptionPane.showConfirmDialog(frame,
                        "Exit OMS Client?", "Confirm Exit", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    aeronRef.close();
                    frame.dispose();
                    System.exit(0);
                }
            }
        });

        Timer connTimer = new Timer(2000, ev -> refreshStatusBar(statusBar, aeronRef));
        connTimer.start();
    }

    private static JPanel buildStatusBar(AeronService aeron) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(70, 70, 70)),
                new EmptyBorder(2, 6, 2, 6)));
        JLabel connLabel = new JLabel();
        connLabel.setFont(connLabel.getFont().deriveFont(11f));
        bar.add(connLabel);
        bar.putClientProperty("connLabel", connLabel);
        refreshStatusBar(bar, aeron);
        return bar;
    }

    private static void refreshStatusBar(JPanel bar, AeronService aeron) {
        JLabel lbl = (JLabel) bar.getClientProperty("connLabel");
        if (lbl == null) return;
        boolean connected = aeron.isConnected();
        lbl.setText(connected
                ? "● Connected to OMS  |  pub: stream 1001  |  sub: stream 1002"
                : "○ Waiting for OMS (stream 1001/1002)...");
        lbl.setForeground(connected ? new Color(80, 200, 80) : new Color(200, 130, 50));
    }
}
