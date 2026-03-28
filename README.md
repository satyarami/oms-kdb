# oms-kdb

Persistence layer for the OMS (Order Management System).  
Listens to order and fill events published by **oms-core** over Aeron IPC, buffers them through a Chronicle Queue, and writes them into **kdb+** tables.

---

## Architecture

```
┌──────────┐   Aeron IPC    ┌─────────────────────┐  Chronicle Queue  ┌────────────┐    q IPC     ┌───────┐
│ oms-core │──────────────►│ AeronChronicleListener│─────────────────►│  KdbWriter  │────────────►│ kdb+  │
│ stream   │   stream 1002  │  (producer side)      │   disk-backed   │ (consumer)  │  upsertOrder│ :5000 │
│ 1002     │                └─────────────────────┘  message log      └────────────┘  insertFill  └───────┘
└──────────┘
```

| Component | Responsibility |
|---|---|
| **AeronChronicleListener** | Subscribes to the Aeron output stream (1002) from oms-core. Writes each raw SBE message (header + body) as a length-prefixed excerpt into a Chronicle Queue. |
| **Chronicle Queue** | Persistent, memory-mapped, disk-backed queue that decouples the Aeron subscriber from the kdb+ writer. Survives restarts — the tailer resumes from where it left off. |
| **KdbWriter** | Tails the Chronicle Queue, decodes each SBE `Order` message (including the embedded `fills` repeating group), and executes `upsertOrder` / `insertFill` against kdb+. |

Both sides of the queue run as **separate processes** so they can be started, stopped, and scaled independently.

---

## Project Structure

```
oms-kdb/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/satya/oms/kdb/
    │   ├── OmsKdbConfig.java            # Centralised config from oms-kdb.properties
    │   ├── KdbConnection.java           # Thin wrapper around the kx c.java client
    │   ├── KdbSchemaInitializer.java     # Boots schema.q into a running kdb+ process
    │   ├── AeronChronicleListener.java   # Aeron subscriber → Chronicle Queue producer
    │   ├── AeronListenerMain.java        # Entry-point for the Aeron listener process
    │   ├── KdbWriter.java                # Chronicle Queue tailer → kdb+ persister
    │   └── KdbWriterMain.java            # Entry-point for the kdb+ writer process
    └── resources/
        ├── oms-kdb.properties            # Aeron, Chronicle Queue, kdb+ settings
        └── schema.q                      # kdb+ table definitions & helper functions
```

---

## Prerequisites

| Dependency | Version | Notes |
|---|---|---|
| Java | 21+ | Required by `maven.compiler.source` |
| Maven | 3.8+ | Build tool |
| Aeron Media Driver | — | Must be running (shared IPC). Started by oms-core's `MediaDriverMain`. |
| oms-core | running | Publishes SBE-encoded Order messages on Aeron stream 1002 |
| oms-common | installed | `mvn install` in the oms-common project first (provides SBE codecs) |
| kdb+ | 4.x | Listening on the configured port (default `localhost:5000`) |

---

## Configuration

All settings live in `src/main/resources/oms-kdb.properties`:

```properties
# Aeron settings
aeron.channel=aeron:ipc?term-length=64k
aeron.out.stream.id=1002

# Chronicle Queue settings
chronicle.queue.path=C:/tmp/oms-chronicle-queue

# kdb+ settings
kdb.host=localhost
kdb.port=5000
```

---

## kdb+ Schema

`schema.q` is executed against kdb+ by `KdbSchemaInitializer` and creates:

### `orders` table

| Column | Type | Description |
|---|---|---|
| orderId | long | Unique order identifier |
| symbolId | int | Instrument / symbol identifier |
| side | symbol | `BUY` or `SELL` |
| quantity | long | Original order quantity |
| price | long | Limit price |
| state | symbol | `NEW`, `FILLED`, `PARTIALLY_FILLED`, `REJECTED` |
| filledQty | long | Cumulative filled quantity |
| remainingQty | long | Remaining open quantity |

### `fills` table

| Column | Type | Description |
|---|---|---|
| orderId | long | Parent order identifier |
| fillQty | long | Quantity of this execution |
| fillPrice | long | Price of this execution |
| executionId | long | Unique execution identifier |

### Helper functions

| Function | Description |
|---|---|
| `upsertOrder[dict]` | Insert or update an order row |
| `insertFill[orderId; dict]` | Append a fill row |
| `fillsForOrder[orderId]` | Select all fills for an order |
| `orderSummary[orderId]` | Order row joined with aggregated fill stats |

---

## SBE Message Format

Messages are encoded with [Simple Binary Encoding](https://github.com/real-logic/simple-binary-encoding) using the schema defined in **oms-common** (`order-schema.xml`).

```
┌──────────────────┬──────────────────────────────────────────────┬───────────────┐
│  MessageHeader   │             Order (block)                    │  fills group  │
│  8 bytes         │  46 bytes                                    │  3-byte hdr   │
│  blockLength     │  orderId  symbolId  side  qty  price         │  + N × 24 B   │
│  templateId      │  state  filledQty  remainingQty              │  fillQty      │
│  schemaId        │                                              │  fillPrice    │
│  version         │                                              │  executionId  │
└──────────────────┴──────────────────────────────────────────────┴───────────────┘
```

---

## Build

```bash
# Install oms-common first (SBE codecs)
cd ../oms-common && mvn clean install

# Build oms-kdb
cd ../oms-kdb && mvn clean compile
```

---

## How to Run

### 1. Start kdb+ on port 5000

```bash
q -p 5000
```

### 2. Initialise the kdb+ schema

```bash
mvn exec:java -Dexec.mainClass=com.satya.oms.kdb.KdbSchemaInitializer
```

This connects to kdb+ and runs every statement in `schema.q` to create the `orders` and `fills` tables plus the helper functions.

### 3. Start the Aeron Media Driver

_(from the oms-core project)_

```bash
# oms-core/scripts
start-media-driver.bat
```

### 4. Start the Aeron Listener (producer side of the queue)

```bash
mvn exec:java -Dexec.mainClass=com.satya.oms.kdb.AeronListenerMain
```

Subscribes to Aeron stream 1002 and writes every SBE message into the Chronicle Queue at the configured path.

### 5. Start the KDB Writer (consumer side of the queue)

```bash
mvn exec:java -Dexec.mainClass=com.satya.oms.kdb.KdbWriterMain
```

Tails the Chronicle Queue, decodes each SBE Order message, and upserts orders / inserts fills into kdb+.

### 6. Publish orders from oms-core

_(from the oms-core project)_

```bash
mvn exec:java -Dexec.mainClass=com.satya.oms.aeron.RandomOrderPublisher
```

### 7. Query kdb+

```q
/ all orders
select from orders

/ fills for a specific order
fillsForOrder 12345

/ order summary with fill aggregates
orderSummary 12345
```

---

## Data Flow (end to end)

```
RandomOrderPublisher                          kdb+ :5000
       │                                           ▲
       │ Aeron IPC stream 1001                     │ upsertOrder / insertFill
       ▼                                           │
   oms-core (Disruptor)                        KdbWriter
       │                                           ▲
       │ Aeron IPC stream 1002                     │ Chronicle Queue tailer
       ▼                                           │
 AeronChronicleListener ──► Chronicle Queue ───────┘
                              (disk-backed)
```

1. **OrderPublisher** sends SBE-encoded orders to oms-core on Aeron stream **1001**.
2. **oms-core** processes them through the LMAX Disruptor, runs order matching / market simulation, and publishes the result (with fills) on Aeron stream **1002**.
3. **AeronChronicleListener** picks up each message from stream 1002 and appends the raw bytes to the Chronicle Queue.
4. **KdbWriter** tails the queue, decodes the SBE message, and persists orders and fills to kdb+ via the `upsertOrder` and `insertFill` helper functions.

---

## Key Dependencies

| Library | Purpose |
|---|---|
| [Aeron](https://github.com/real-logic/aeron) | Ultra-low-latency IPC messaging |
| [SBE](https://github.com/real-logic/simple-binary-encoding) | Zero-copy binary message encoding (via oms-common) |
| [Chronicle Queue](https://github.com/OpenHFT/Chronicle-Queue) | Persistent, memory-mapped message queue |
| [javakdb](https://github.com/KxSystems/javakdb) | Java client for kdb+ (c.java) |
| [SLF4J](https://www.slf4j.org/) | Logging facade (simple binding) |
