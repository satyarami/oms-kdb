package com.satya.oms.model;

/**
 * Immutable snapshot of a single execution fill received on Aeron stream 1002.
 */
public class FillRecord {

    private final long executionId;
    private final long fillQty;
    private final long fillPrice;   // price in ticks (divide by 100 for display)

    public FillRecord(long executionId, long fillQty, long fillPrice) {
        this.executionId = executionId;
        this.fillQty     = fillQty;
        this.fillPrice   = fillPrice;
    }

    public long getExecutionId() { return executionId; }
    public long getFillQty()     { return fillQty; }
    public long getFillPrice()   { return fillPrice; }

    /** Display price as decimal (ticks / 100). */
    public String fillPriceDisplay() {
        return String.format("%.2f", fillPrice / 100.0);
    }

    @Override
    public String toString() {
        return "Fill[execId=" + executionId + " qty=" + fillQty + " px=" + fillPriceDisplay() + "]";
    }
}
