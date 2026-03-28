package com.satya.oms.kdb;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry-point for the <b>Chronicle Queue → kdb+</b> writer process.
 *
 * <p>Tails the Chronicle Queue written by {@link AeronListenerMain},
 * decodes each SBE Order message and persists orders and fills to kdb+.
 *
 * <pre>
 *   mvn exec:java -Dexec.mainClass=com.satya.oms.kdb.KdbWriterMain
 * </pre>
 */
public class KdbWriterMain {

    private static final Logger LOG = LoggerFactory.getLogger(KdbWriterMain.class);

    public static void main(String[] args) {
        String queuePath = OmsKdbConfig.getChronicleQueuePath();
        String kdbHost   = OmsKdbConfig.getKdbHost();
        int    kdbPort   = OmsKdbConfig.getKdbPort();

        LOG.info("Chronicle Queue path : {}", queuePath);
        LOG.info("kdb+ target          : {}:{}", kdbHost, kdbPort);

        try (ChronicleQueue queue = SingleChronicleQueueBuilder
                     .single(queuePath)
                     .build();
             KdbConnection kdb = new KdbConnection(kdbHost, kdbPort)) {

            kdb.connect();

            KdbWriter writer = new KdbWriter(queue, kdb);

            // Graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutdown signal received – stopping writer");
                writer.stop();
            }));

            // Run on the main thread (blocks until stopped)
            writer.run();

        } catch (Exception e) {
            LOG.error("KdbWriterMain failed", e);
            System.exit(1);
        }
    }
}
