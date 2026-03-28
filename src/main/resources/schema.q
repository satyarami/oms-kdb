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
orderfills:([] orderId:`long$(); fillQty:`long$(); fillPrice:`long$(); executionId:`long$())

/ ── Keyed versions (optional) ──────────────────────────
orders_keyed:1!orders
orderfills_keyed:2!orderfills

/ ── Helper: insert order ───────────────────────────────
upsertOrder:{[o] `orders upsert `orderId`symbolId`side`quantity`price`state`filledQty`remainingQty!(o`orderId; o`symbolId; o`side; o`quantity; o`price; o`state; o`filledQty; o`remainingQty)}

/ ── Helper: insert fill ────────────────────────────────
insertFill:{[oid;f] `orderfills insert `orderId`fillQty`fillPrice`executionId!(oid; f`fillQty; f`fillPrice; f`executionId)}

/ ── Queries ────────────────────────────────────────────
fillsForOrder:{[oid] select from orderfills where orderId=oid}
orderSummary:{[oid] o:select from orders where orderId=oid; f:select numFills:count i, totalFillQty:sum fillQty, avgFillPrice:avg fillPrice from orderfills where orderId=oid; o,'f}