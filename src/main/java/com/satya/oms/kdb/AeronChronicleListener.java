package com.satya.oms.kdb;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes to the Aeron output stream from oms-core (stream 1002) and writes
 * every inbound SBE message – header included – as a raw byte excerpt into a
 * Chronicle Queue.  This is the <b>producer</b> side of the queue; the
 * {@link KdbWriter} is the consumer side.
 *
 * <p>Run via {@link AeronListenerMain}.
 */
public class AeronChronicleListener implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(AeronChronicleListener.class);

    private final Subscription subscription;
    private final ChronicleQueue queue;
    private final IdleStrategy idleStrategy = new SleepingMillisIdleStrategy(1);
    private volatile boolean running = true;

    public AeronChronicleListener(Aeron aeron, ChronicleQueue queue) {
        this.queue = queue;
        String channel = OmsKdbConfig.getAeronChannel();
        int streamId = OmsKdbConfig.getAeronOutStreamId();
        this.subscription = aeron.addSubscription(channel, streamId);
        LOG.info("Subscribed to Aeron channel={} stream={}", channel, streamId);
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        final FragmentHandler handler = this::onFragment;
        LOG.info("AeronChronicleListener polling loop started");

        while (running && !Thread.currentThread().isInterrupted()) {
            int fragments = subscription.poll(handler, 10);
            if (fragments == 0) {
                idleStrategy.idle();
            }
        }

        LOG.info("AeronChronicleListener polling loop stopped");
    }

    /**
     * Called for each Aeron fragment.  We write the complete SBE message
     * (header + body) as a single Chronicle Queue excerpt so the downstream
     * {@link KdbWriter} can decode it identically.
     */
    private void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        try (ExcerptAppender appender = queue.createAppender()) {
            appender.writeBytes(b -> {
                // write message length prefix so the tailer knows how many bytes to read
                b.writeInt(length);
                for (int i = 0; i < length; i++) {
                    b.writeByte(buffer.getByte(offset + i));
                }
            });
            LOG.debug("Wrote {} bytes to Chronicle Queue", length);
        }
    }
}
