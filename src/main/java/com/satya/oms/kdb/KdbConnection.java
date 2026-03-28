package com.satya.oms.kdb;

import com.kx.c;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

/**
 * Thin wrapper around the kx {@link c} client, providing connect / execute / close.
 */
public class KdbConnection implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(KdbConnection.class);

    private final String host;
    private final int port;
    private c connection;

    public KdbConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Open a connection to the kdb+ process. */
    public void connect() throws c.KException, IOException {
        LOG.info("Connecting to kdb+ at {}:{}", host, port);
        connection = new c(host, port);
        LOG.info("Connected to kdb+ at {}:{}", host, port);
    }

    /**
     * Execute a q expression synchronously and return the result.
     */
    public Object execute(String qExpression) throws c.KException, IOException {
        if (connection == null) {
            throw new IllegalStateException("Not connected to kdb+. Call connect() first.");
        }
        LOG.debug("Executing q: {}", qExpression);
        return connection.k(qExpression);
    }

    /**
     * Execute a q expression asynchronously (fire-and-forget).
     */
    public void executeAsync(String qExpression) throws IOException {
        if (connection == null) {
            throw new IllegalStateException("Not connected to kdb+. Call connect() first.");
        }
        LOG.debug("Executing async q: {}", qExpression);
        connection.ks(qExpression);
    }

    /** Check whether the connection is open. */
    public boolean isConnected() {
        return connection != null && connection.s != null && connection.s.isConnected();
    }

    @Override
    public void close() throws IOException {
        if (connection != null) {
            LOG.info("Closing kdb+ connection to {}:{}", host, port);
            connection.close();
            connection = null;
        }
    }
}
