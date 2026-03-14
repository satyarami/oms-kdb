package com.satya.oms.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central, thread-safe store of all OrderRecords.
 * The Aeron subscriber thread writes via addOrUpdate; the EDT reads via getOrders().
 */
public class OrderStore {

    public interface Listener {
        void onOrderChanged(OrderRecord order);
    }

    private final Map<Long, OrderRecord>  map       = new LinkedHashMap<>();
    private final List<OrderRecord>       ordered   = new ArrayList<>();
    private final List<Listener>          listeners = new CopyOnWriteArrayList<>();

    public synchronized void addOrder(OrderRecord rec) {
        map.put(rec.getOrderId(), rec);
        ordered.add(rec);
        fireChanged(rec);
    }

    /** Called from Aeron subscriber thread; dispatches to listeners (which post to EDT). */
    public synchronized void applyExecution(long orderId, long symbolId, byte side,
                                             long quantity, long price, String state,
                                             long filledQty, long remainingQty,
                                             List<FillRecord> fills, String rawMessage) {
        OrderRecord rec = map.get(orderId);
        if (rec == null) {
            // Order not created by this UI — create it from execution report
            OrderRecord.Side orderSide = (side == 0) ? OrderRecord.Side.BUY : OrderRecord.Side.SELL;
            String symbol = symbolIdToString(symbolId);
            rec = new OrderRecord(orderId, symbol, orderSide, quantity, price);
            map.put(orderId, rec);
            ordered.add(rec);
        }
        rec.applyExecution(state, filledQty, remainingQty, fills, rawMessage);
        fireChanged(rec);
    }

    /** Convert symbolId to symbol string (simple mapping). */
    private static String symbolIdToString(long symbolId) {
        return switch ((int) symbolId) {
            case 1 -> "AAPL";
            case 2 -> "MSFT";
            case 3 -> "GOOGL";
            case 4 -> "AMZN";
            case 5 -> "TSLA";
            case 6 -> "NVDA";
            default -> "SYM" + symbolId;
        };
    }

    public synchronized List<OrderRecord> getOrders() {
        return new ArrayList<>(ordered);
    }

    public void addListener(Listener l)    { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    private void fireChanged(OrderRecord rec) {
        for (Listener l : listeners) l.onOrderChanged(rec);
    }
}
