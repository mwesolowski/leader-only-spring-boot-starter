package pl.allegro.tech.leader.only.curator;

import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.slf4j.Logger;
import pl.allegro.tech.leader.only.api.LeaderLatchCannotStartException;
import pl.allegro.tech.leader.only.api.LeaderLatchCannotStopException;
import pl.allegro.tech.leader.only.api.Leadership;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.slf4j.LoggerFactory.getLogger;

final class CuratorLeadership implements Leadership, Closeable {

    private static final Logger logger = getLogger(CuratorLeadership.class);

    private static final Duration MAX_WAITING_FOR_START_TIMEOUT = Duration.ofSeconds(30);
    private static final long WAITING_STEP_MS = 100;

    private final LeaderLatch leaderLatch;

    private final AtomicBoolean isLeaderLatchStarted = new AtomicBoolean(false);

    public CuratorLeadership(LeaderLatch leaderLatch) {
        this.leaderLatch = leaderLatch;

        try {
            leaderLatch.start();
        } catch (Exception e) {
            throw new LeaderLatchCannotStartException(e);
        }

        leaderLatch.addListener(new LeaderLatchListener() {
            final String hostname = resolveHostname();

            @Override
            public void isLeader() {
                isLeaderLatchStarted.set(true);
                logger.info("{} is selected for the leader", hostname);
            }

            @Override
            public void notLeader() {
                isLeaderLatchStarted.set(true);
                logger.info("{} is not selected for the leader", hostname);
            }

            private String resolveHostname() {
                try {
                    return InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException e) {
                    return "???";
                }
            }
        });

        try {
            awaitStarted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean hasLeadership() {
        return leaderLatch.hasLeadership();
    }

    @Override
    public void close() {
        try {
            leaderLatch.close();
        } catch (IOException e) {
            throw new LeaderLatchCannotStopException(e);
        }
    }

    private void awaitStarted() throws InterruptedException {
        synchronized(this)
        {
            long waitNanos = MAX_WAITING_FOR_START_TIMEOUT.toNanos();

            while (!isLeaderLatchStarted.get() && waitNanos > 0) {
                long startNanos = System.nanoTime();
                TimeUnit.MILLISECONDS.timedWait(this, WAITING_STEP_MS);
                long elapsed = System.nanoTime() - startNanos;
                waitNanos -= elapsed;
            }

            if (!isLeaderLatchStarted.get()) {
                throw new LeaderLatchCannotStartException();
            }
        }
    }
}
