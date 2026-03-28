package com.satya.oms.kdb;

import io.aeron.Aeron;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry-point for the <b>Aeron → Chronicle Queue</b> bridge process.
 *
 * <p>Subscribes to the oms-core Aeron output stream (stream 1002) and
 * writes every SBE message into a Chronicle Queue so that the
 * {@link KdbWriterMain} process can persist them to kdb+.
 *
 * <pre>
 *   mvn exec:java -Dexec.mainClass=com.satya.oms.kdb.AeronListenerMain
 * </pre>
 */
public class AeronListenerMain {

    private static final Logger LOG = LoggerFactory.getLogger(AeronListenerMain.class);

    public static void main(String[] args) {
        String queuePath = OmsKdbConfig.getChronicleQueuePath();
        LOG.info("Chronicle Queue path: {}", queuePath);

        try (Aeron aeron = Aeron.connect(new Aeron.Context());
             ChronicleQueue queue = SingleChronicleQueueBuilder
                     .single(queuePath)
                     .build()) {

            LOG.info("Connected to Aeron Media Driver");

            AeronChronicleListener listener = new AeronChronicleListener(aeron, queue);

            // Graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutdown signal received – stopping listener");
                listener.stop();
            }));

            // Run on the main thread (blocks until stopped)
            listener.run();

        } catch (Exception e) {
            LOG.error("AeronListenerMain failed", e);
            System.exit(1);
        }
    }
}
