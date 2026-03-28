package com.satya.oms.kdb;

import com.satya.oms.sbe.MessageHeaderDecoder;
import com.satya.oms.sbe.OrderDecoder;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic tool that tails the Chronicle Queue, decodes every SBE Order
 * message (including fills) and prints the contents to stdout.
 *
 * <p>Useful for verifying the Aeron → Chronicle Queue pipeline without
 * needing a running kdb+ instance.
 *
 * <pre>
 *   mvn exec:java -Dexec.mainClass=com.satya.oms.kdb.ChronicleQueueMonitor
 * </pre>
 */
public class ChronicleQueueMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(ChronicleQueueMonitor.class);
    private static final String SEPARATOR = "─".repeat(72);

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final OrderDecoder orderDecoder = new OrderDecoder();

    private volatile boolean running = true;
    private long messageCount = 0;

    public void stop() {
        running = false;
    }

    public void run(ChronicleQueue queue) {
        System.out.println(SEPARATOR);
        System.out.println("  Chronicle Queue Monitor");
        System.out.println("  Queue path: " + OmsKdbConfig.getChronicleQueuePath());
        System.out.println("  Tailing from latest … (Ctrl+C to stop)");
        System.out.println(SEPARATOR);
        System.out.println();

        try (ExcerptTailer tailer = queue.createTailer("queue-monitor")) {
            while (running && !Thread.currentThread().isInterrupted()) {
                boolean read = tailer.readBytes(bytes -> {
                    int length = bytes.readInt();
                    byte[] raw = new byte[length];
                    for (int i = 0; i < length; i++) {
                        raw[i] = bytes.readByte();
                    }
                    decode(raw, length);
                });

                if (!read) {
                    Thread.yield();
                }
            }
        }

        System.out.println();
        System.out.println(SEPARATOR);
        System.out.printf("  Monitor stopped. %d message(s) displayed.%n", messageCount);
        System.out.println(SEPARATOR);
    }

    private void decode(byte[] raw, int length) {
        UnsafeBuffer buffer = new UnsafeBuffer(raw);

        headerDecoder.wrap(buffer, 0);
        int headerLen   = headerDecoder.encodedLength();
        int blockLength = headerDecoder.blockLength();
        int version     = headerDecoder.version();

        orderDecoder.wrap(buffer, headerLen, blockLength, version);

        messageCount++;

        long   orderId      = orderDecoder.orderId();
        long   symbolId     = orderDecoder.symbolId();
        String side         = orderDecoder.side().name();
        long   quantity     = orderDecoder.quantity();
        long   price        = orderDecoder.price();
        String state        = orderDecoder.state().name();
        long   filledQty    = orderDecoder.filledQty();
        long   remainingQty = orderDecoder.remainingQty();

        System.out.printf("[%d] ORDER  orderId=%-10d symbol=%-6d side=%-4s qty=%-8d price=%-10d state=%-18s filled=%-8d remaining=%-8d%n",
                messageCount, orderId, symbolId, side, quantity, price, state, filledQty, remainingQty);

        OrderDecoder.FillsDecoder fills = orderDecoder.fills();
        int fillCount = fills.count();
        if (fillCount > 0) {
            int idx = 0;
            while (fills.hasNext()) {
                fills.next();
                idx++;
                System.out.printf("       FILL  [%d/%d]  execId=%-16d qty=%-8d price=%-10d%n",
                        idx, fillCount, fills.executionId(), fills.fillQty(), fills.fillPrice());
            }
        }
    }

    // ── main ──────────────────────────────────────────────────
    public static void main(String[] args) {
        String queuePath = OmsKdbConfig.getChronicleQueuePath();
        LOG.info("Opening Chronicle Queue at {}", queuePath);

        try (ChronicleQueue queue = SingleChronicleQueueBuilder
                .single(queuePath)
                .build()) {

            ChronicleQueueMonitor monitor = new ChronicleQueueMonitor();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutdown signal received – stopping monitor");
                monitor.stop();
            }));

            monitor.run(queue);

        } catch (Exception e) {
            LOG.error("ChronicleQueueMonitor failed", e);
            System.exit(1);
        }
    }
}
