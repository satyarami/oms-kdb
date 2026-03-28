package com.satya.oms.kdb;

import com.kx.c;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Thin wrapper around the kx {@link c} client, providing connect / execute / close
 * with connection-timeout and automatic reconnection support.
 */
public class KdbConnection implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(KdbConnection.class);

    /** TCP connect timeout in milliseconds. */
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Maximum number of reconnection attempts before giving up. */
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    /** Delay between reconnection attempts in milliseconds. */
    private static final long RECONNECT_DELAY_MS = 3_000;

    private final String host;
    private final int port;
    private c connection;

    public KdbConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Open a connection to the kdb+ process.
     * Uses a bounded timeout so the caller never blocks indefinitely.
     */
    public void connect() throws c.KException, IOException {
        LOG.info("Connecting to kdb+ at {}:{} (timeout {}ms)", host, port, CONNECT_TIMEOUT_MS);

        // Probe the port first with a timeout so we don't block forever
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        }
        // Port is reachable – open the real kx connection
        connection = new c(host, port);
        LOG.info("Connected to kdb+ at {}:{}", host, port);
    }

    /**
     * Attempt to reconnect, retrying up to {@link #MAX_RECONNECT_ATTEMPTS} times.
     *
     * @return {@code true} if reconnection succeeded
     */
    public boolean reconnect() {
        for (int attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS; attempt++) {
            LOG.warn("Reconnect attempt {}/{} to kdb+ at {}:{}", attempt, MAX_RECONNECT_ATTEMPTS, host, port);
            closeQuietly();
            try {
                connect();
                return true;
            } catch (Exception e) {
                LOG.warn("Reconnect attempt {} failed: {}", attempt, e.getMessage());
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        LOG.error("All {} reconnect attempts to kdb+ exhausted", MAX_RECONNECT_ATTEMPTS);
        return false;
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

    /** Close silently, ignoring exceptions. */
    private void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
