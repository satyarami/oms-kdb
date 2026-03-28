package com.satya.oms.kdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Reads {@code schema.q} from the classpath and executes each statement
 * against a running kdb+ process to create the orders and fills tables.
 *
 * <p>Usage: {@code java com.satya.oms.kdb.KdbSchemaInitializer}
 */
public class KdbSchemaInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(KdbSchemaInitializer.class);
    private static final String SCHEMA_RESOURCE = "schema.q";
    private static final String PROPS_RESOURCE = "oms-kdb.properties";

    public static void main(String[] args) {
        try {
            // ── Load kdb+ connection settings ─────────────────────
            Properties props = new Properties();
            try (InputStream pis = KdbSchemaInitializer.class.getClassLoader()
                    .getResourceAsStream(PROPS_RESOURCE)) {
                if (pis == null) {
                    throw new IllegalStateException("Could not find " + PROPS_RESOURCE + " on classpath");
                }
                props.load(pis);
            }

            String host = props.getProperty("kdb.host", "localhost");
            int port = Integer.parseInt(props.getProperty("kdb.port", "5000"));

            // ── Read schema.q from classpath ──────────────────────
            InputStream sis = KdbSchemaInitializer.class.getClassLoader()
                    .getResourceAsStream(SCHEMA_RESOURCE);
            if (sis == null) {
                throw new IllegalStateException("Could not find " + SCHEMA_RESOURCE + " on classpath");
            }

            // ── Connect and execute ───────────────────────────────
            try (KdbConnection kdb = new KdbConnection(host, port)) {
                kdb.connect();

                // Read the schema file, accumulate multi-line statements, and execute
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(sis, StandardCharsets.UTF_8))) {

                    StringBuilder statement = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();

                        // skip blank lines and comments
                        if (trimmed.isEmpty() || trimmed.startsWith("/")) {
                            // if we have an accumulated statement, execute it first
                            if (!statement.isEmpty()) {
                                executeStatement(kdb, statement.toString());
                                statement.setLength(0);
                            }
                            continue;
                        }

                        // Accumulate continuation lines (those starting with whitespace)
                        if (!line.isEmpty() && Character.isWhitespace(line.charAt(0)) && !statement.isEmpty()) {
                            statement.append(" ").append(trimmed);
                        } else {
                            // Execute any previous accumulated statement
                            if (!statement.isEmpty()) {
                                executeStatement(kdb, statement.toString());
                                statement.setLength(0);
                            }
                            statement.append(trimmed);
                        }
                    }

                    // Execute last accumulated statement
                    if (!statement.isEmpty()) {
                        executeStatement(kdb, statement.toString());
                    }
                }

                // ── Verify tables were created ────────────────────
                LOG.info("Verifying tables...");
                Object orderMeta = kdb.execute("meta orders");
                LOG.info("orders table meta: {}", orderMeta);
                Object fillMeta = kdb.execute("meta fills");
                LOG.info("fills table meta: {}", fillMeta);

                LOG.info("Schema initialization complete.");
            }
        } catch (Exception e) {
            LOG.error("Failed to initialize kdb+ schema", e);
            System.exit(1);
        }
    }

    private static void executeStatement(KdbConnection kdb, String statement) throws Exception {
        LOG.info(">> {}", statement);
        Object result = kdb.execute(statement);
        if (result != null) {
            LOG.info("<< {}", result);
        }
    }
}
