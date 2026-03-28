/ ─────────────────────────────────────────────────────
/ OMS kdb+ table definitions
/ Generated from order-schema.xml (SBE schema v1.0.0)
/ ─────────────────────────────────────────────────────

/ ── Enum mappings ──────────────────────────────────────
side:`BUY`SELL
orderState:`NEW`FILLED`PARTIALLY_FILLED`REJECTED

/ ── Orders table ───────────────────────────────────────
orders:([] 
    orderId:      `long$();        / uint64 – unique order identifier
    symbolId:     `int$();         / uint32 – instrument symbol id
    side:         `symbol$();      / Side enum (BUY / SELL)
    quantity:     `long$();        / uint64 – order quantity
    price:        `long$();        / uint64 – order price (raw/scaled)
    state:        `symbol$();      / OrderState enum (NEW / FILLED / PARTIALLY_FILLED / REJECTED)
    filledQty:    `long$();        / uint64 – cumulative filled quantity
    remainingQty: `long$()         / uint64 – remaining quantity
 )

/ ── Fills table ────────────────────────────────────────
fills:([]
    orderId:     `long$();         / uint64 – parent order id (foreign key → orders.orderId)
    fillQty:     `long$();         / uint64 – fill quantity
    fillPrice:   `long$();         / uint64 – fill price (raw/scaled)
    executionId: `long$()          / uint64 – unique execution identifier
 )

/ ── Keyed versions (optional) ──────────────────────────
orders_keyed:1!orders              / keyed on orderId
fills_keyed:2!fills                / keyed on orderId, executionId

/ ── Helper: insert order ───────────────────────────────
upsertOrder:{[o]
    `orders upsert `orderId`symbolId`side`quantity`price`state`filledQty`remainingQty!
        (o`orderId; o`symbolId; o`side; o`quantity; o`price; o`state; o`filledQty; o`remainingQty)
 }

/ ── Helper: insert fill ────────────────────────────────
insertFill:{[oid;f]
    `fills insert `orderId`fillQty`fillPrice`executionId!
        (oid; f`fillQty; f`fillPrice; f`executionId)
 }

/ ── Queries ────────────────────────────────────────────
/ Get all fills for a given order
fillsForOrder:{[oid] select from fills where orderId=oid}

/ Get order with aggregated fill info
orderSummary:{[oid]
    o:select from orders where orderId=oid;
    f:select numFills:count i, totalFillQty:sum fillQty, avgFillPrice:avg fillPrice from fills where orderId=oid;
    o,'f
 }
