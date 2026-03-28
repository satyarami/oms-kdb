/ ─────────────────────────────────────────────────────
/ OMS kdb+ table definitions
/ Generated from order-schema.xml (SBE schema v1.0.0)
/ ─────────────────────────────────────────────────────

/ ── Enum mappings ──────────────────────────────────────
side:`BUY`SELL
orderState:`NEW`FILLED`PARTIALLY_FILLED`REJECTED

/ ── Orders table ───────────────────────────────────────
orders:([] orderId:`long$(); symbolId:`int$(); side:`symbol$(); quantity:`long$(); price:`long$(); state:`symbol$(); filledQty:`long$(); remainingQty:`long$())

/ ── Fills table ────────────────────────────────────────
fills:([] orderId:`long$(); fillQty:`long$(); fillPrice:`long$(); executionId:`long$())

/ ── Keyed versions (optional) ──────────────────────────
orders_keyed:1!orders
fills_keyed:2!fills

/ ── Helper: insert order ───────────────────────────────
upsertOrder:{[o] `orders upsert `orderId`symbolId`side`quantity`price`state`filledQty`remainingQty!(o`orderId; o`symbolId; o`side; o`quantity; o`price; o`state; o`filledQty; o`remainingQty)}

/ ── Helper: insert fill ────────────────────────────────
insertFill:{[oid;f] `fills insert `orderId`fillQty`fillPrice`executionId!(oid; f`fillQty; f`fillPrice; f`executionId)}

/ ── Queries ────────────────────────────────────────────
fillsForOrder:{[oid] select from fills where orderId=oid}
orderSummary:{[oid] o:select from orders where orderId=oid; f:select numFills:count i, totalFillQty:sum fillQty, avgFillPrice:avg fillPrice from fills where orderId=oid; o,'f}