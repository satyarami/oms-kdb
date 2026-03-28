package com.satya.oms.kdb;

import com.satya.oms.sbe.MessageHeaderDecoder;
import com.satya.oms.sbe.OrderDecoder;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tails the Chronicle Queue, decodes each SBE Order message (including its
 * fills group) and persists the data into kdb+ using the helper functions
 * defined in {@code schema.q} ({@code upsertOrder} and {@code insertFill}).
 *
 * <p>Run via {@link KdbWriterMain}.
 */
public class KdbWriter implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(KdbWriter.class);

    private final ChronicleQueue queue;
    private final KdbConnection kdb;
    private volatile boolean running = true;

    // SBE decoders – reused across messages (single-threaded tailer)
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final OrderDecoder orderDecoder = new OrderDecoder();

    public KdbWriter(ChronicleQueue queue, KdbConnection kdb) {
        this.queue = queue;
        this.kdb = kdb;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        LOG.info("KdbWriter tailer loop started");

        try (ExcerptTailer tailer = queue.createTailer("kdb-writer")) {
            while (running && !Thread.currentThread().isInterrupted()) {
                boolean read = tailer.readBytes(bytes -> {
                    int length = bytes.readInt();
                    byte[] raw = new byte[length];
                    for (int i = 0; i < length; i++) {
                        raw[i] = bytes.readByte();
                    }
                    processMessage(raw, length);
                });

                if (!read) {
                    // nothing new – back off a little
                    Thread.yield();
                }
            }
        }

        LOG.info("KdbWriter tailer loop stopped");
    }

    // -----------------------------------------------------------------------
    //  SBE decode → kdb+ persist
    // -----------------------------------------------------------------------
    private void processMessage(byte[] raw, int length) {
        UnsafeBuffer buffer = new UnsafeBuffer(raw);

        // Decode SBE header
        headerDecoder.wrap(buffer, 0);
        int headerLen = headerDecoder.encodedLength();
        int blockLength = headerDecoder.blockLength();
        int version = headerDecoder.version();

        // Decode Order body
        orderDecoder.wrap(buffer, headerLen, blockLength, version);

        long orderId     = orderDecoder.orderId();
        long symbolId    = orderDecoder.symbolId();
        String side      = orderDecoder.side().name();         // BUY / SELL
        long quantity    = orderDecoder.quantity();
        long price       = orderDecoder.price();
        String state     = orderDecoder.state().name();        // NEW / FILLED / …
        long filledQty   = orderDecoder.filledQty();
        long remainingQty = orderDecoder.remainingQty();

        // ── Upsert order ──────────────────────────────────────
        try {
            String upsertQ = String.format(
                    "upsertOrder `orderId`symbolId`side`quantity`price`state`filledQty`remainingQty!" +
                            "(%dj;%di;`%s;%dj;%dj;`%s;%dj;%dj)",
                    orderId, symbolId, side, quantity, price, state, filledQty, remainingQty);
            kdb.execute(upsertQ);
            LOG.info("Upserted order {} state={}", orderId, state);
        } catch (Exception e) {
            LOG.error("Failed to upsert order {}", orderId, e);
        }

        // ── Insert fills ──────────────────────────────────────
        OrderDecoder.FillsDecoder fills = orderDecoder.fills();
        int fillCount = fills.count();
        if (fillCount > 0) {
            LOG.info("Order {} has {} fill(s)", orderId, fillCount);
            while (fills.hasNext()) {
                fills.next();
                long fillQty    = fills.fillQty();
                long fillPrice  = fills.fillPrice();
                long executionId = fills.executionId();

                try {
                    String insertQ = String.format(
                            "insertFill[%dj;`fillQty`fillPrice`executionId!(%dj;%dj;%dj)]",
                            orderId, fillQty, fillPrice, executionId);
                    kdb.execute(insertQ);
                    LOG.info("  Inserted fill execId={} qty={} price={}", executionId, fillQty, fillPrice);
                } catch (Exception e) {
                    LOG.error("  Failed to insert fill execId={}", executionId, e);
                }
            }
        }
    }
}
