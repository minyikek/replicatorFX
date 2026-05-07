package com.replicatorfx;

import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;

import java.util.concurrent.CountDownLatch;

public final class MediaDriverMain {

    public static void main(String[] args) throws InterruptedException {
        MediaDriver.Context ctx = new MediaDriver.Context()
            .threadingMode(ThreadingMode.DEDICATED)       // dedicated conductor, sender, receiver threads
            .conductorIdleStrategy(new BusySpinIdleStrategy())
            .senderIdleStrategy(new BusySpinIdleStrategy())
            .receiverIdleStrategy(new BusySpinIdleStrategy())
            .sharedIdleStrategy(new NoOpIdleStrategy())
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true);

        try (MediaDriver driver = MediaDriver.launch(ctx)) {
            System.out.println("[media-driver] started");
            System.out.println("[media-driver] aeron dir : " + driver.aeronDirectoryName());
            System.out.println("[media-driver] threading : " + ctx.threadingMode());
            System.out.println("[media-driver] press Ctrl+C to stop");

            CountDownLatch shutdown = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown, "media-driver-shutdown"));
            shutdown.await();
        }

        System.out.println("[media-driver] stopped");
    }
}
