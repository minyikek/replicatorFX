package com.replicatorfx.publisher;

import com.replicatorfx.config.SimulatorConfig;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.CloseHelper;
import org.agrona.MutableDirectBuffer;

public final class AeronPublisher implements AutoCloseable {

    private final Aeron       aeron;
    private final Publication publication;

    public AeronPublisher(SimulatorConfig config) {
        this.aeron       = Aeron.connect();
        this.publication = aeron.addPublication(config.aeron.channel, config.aeron.streamId);
        awaitConnected();
    }

    public void offer(MutableDirectBuffer buffer, int offset, int length) {
        for (int attempt = 0; attempt < 10; attempt++) {
            long result = publication.offer(buffer, offset, length);
            if (result > 0) return;
            if (result == Publication.CLOSED || result == Publication.MAX_POSITION_EXCEEDED) {
                System.err.println("[publisher] fatal offer result: " + result);
                return;
            }
            Thread.onSpinWait();
        }
    }

    private void awaitConnected() {
        System.out.print("[publisher] waiting for subscriber connection");
        while (!publication.isConnected()) {
            try { Thread.sleep(200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            System.out.print(".");
        }
        System.out.println(" connected.");
    }

    @Override
    public void close() {
        CloseHelper.close(publication);
        CloseHelper.close(aeron);
    }
}
