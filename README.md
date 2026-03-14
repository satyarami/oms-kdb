# OMS UI — Independent Client

A Java Swing client for the OMS trading system. Sends orders via Aeron IPC and displays live execution reports in a blotter.

## Architecture

```
[OMS UI]  --stream 1001-->  [Aeron IPC]  --> [OMS Core]
[OMS UI]  <--stream 1002--  [Aeron IPC]  <-- [OMS Core]
```

| Stream | Direction | Content |
|--------|-----------|---------|
| 1001   | UI → OMS  | New orders (SBE encoded) |
| 1002   | OMS → UI  | Execution reports / rejects (SBE encoded) |

## Project Structure

```
src/main/java/com/satya/oms/
├── App.java                        ← Main entry point (JFrame + tabs + status bar)
├── aeron/
│   └── AeronService.java           ← Publish on 1001, subscribe on 1002
├── model/
│   ├── FillRecord.java             ← Immutable fill snapshot
│   ├── OrderRecord.java            ← Mutable order + accumulated fills
│   └── OrderStore.java             ← Thread-safe store, fires listeners
└── ui/
    ├── OrderEntryPanel.java        ← Order Entry tab
    └── OrderBlotterPanel.java      ← Order Blotter tab

src/main/resources/
└── order-schema.xml                ← SBE schema (generates codecs at build time)
```

## Prerequisites

1. **Java 17+**
2. **Aeron Media Driver running** — start from the OMS core scripts:
   ```
   scripts\start-media-driver.bat
   ```
3. **OMS Core running** — subscribes on stream 1001, publishes fills on 1002:
   ```
   scripts\start-order-subscriber.bat
   ```

## Build

```bash
cd C:\Project\oms-ui
mvn package -DskipTests
```

Produces: `target/oms-ui-1.0-SNAPSHOT.jar`  (fat jar, all dependencies included)

## Run

```bash
java -jar target\oms-ui-1.0-SNAPSHOT.jar
```

## UI Tabs

### Order Entry
| Field    | Description                          |
|----------|--------------------------------------|
| Symbol   | AAPL / MSFT / GOOGL / AMZN / TSLA / NVDA |
| Side     | BUY or SELL                          |
| Quantity | Whole number > 0                     |
| Price    | Decimal e.g. `123.45`               |

Click **Send Order** — the order is SBE-encoded and published on Aeron stream 1001.

### Order Blotter
- Each submitted order appears as a row immediately.
- When execution reports arrive on stream 1002, fills appear as **indented sub-rows** directly under the parent order.
- State column is colour-coded: 🟢 FILLED · 🟡 PARTIAL · 🔴 REJECTED
- Status bar shows live connection state (green = OMS connected, amber = waiting).
