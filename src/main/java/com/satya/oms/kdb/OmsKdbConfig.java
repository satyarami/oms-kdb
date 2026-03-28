package com.satya.oms.kdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralised configuration loaded from {@code oms-kdb.properties}.
 */
public final class OmsKdbConfig {

    private static final Logger LOG = LoggerFactory.getLogger(OmsKdbConfig.class);
    private static final String PROPS_FILE = "oms-kdb.properties";
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream is = OmsKdbConfig.class.getClassLoader().getResourceAsStream(PROPS_FILE)) {
            if (is != null) {
                PROPS.load(is);
                LOG.info("Loaded configuration from {}", PROPS_FILE);
            } else {
                LOG.warn("{} not found on classpath – using defaults", PROPS_FILE);
            }
        } catch (IOException e) {
            LOG.error("Failed to load {}", PROPS_FILE, e);
        }
    }

    private OmsKdbConfig() {}

    // ── Aeron ─────────────────────────────────────────────────
    public static String getAeronChannel() {
        return PROPS.getProperty("aeron.channel", "aeron:ipc?term-length=64k");
    }

    public static int getAeronOutStreamId() {
        return Integer.parseInt(PROPS.getProperty("aeron.out.stream.id", "1002"));
    }

    // ── Chronicle Queue ───────────────────────────────────────
    public static String getChronicleQueuePath() {
        return PROPS.getProperty("chronicle.queue.path", "C:/tmp/oms-chronicle-queue");
    }

    // ── KDB+ ──────────────────────────────────────────────────
    public static String getKdbHost() {
        return PROPS.getProperty("kdb.host", "localhost");
    }

    public static int getKdbPort() {
        return Integer.parseInt(PROPS.getProperty("kdb.port", "5000"));
    }
}
