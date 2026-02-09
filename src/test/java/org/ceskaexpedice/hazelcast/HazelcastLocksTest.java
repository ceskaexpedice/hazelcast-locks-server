package org.ceskaexpedice.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import org.ceskaexpedice.hazelcast.HazelcastClientNode;
import org.ceskaexpedice.hazelcast.HazelcastConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazelcastLocksTest {
    private static HazelcastClientNode hazelcastClientNode;

    @BeforeAll
    static void beforeAll() {
        HazelcastConfiguration hazelcastConfig = createHazelcastConfig();
        HazelcastServerNode.ensureHazelcastNode(hazelcastConfig);

        hazelcastClientNode = new HazelcastClientNode();
        ensureHazelcastClientNode(hazelcastConfig);

    }

    @AfterAll
    static void afterAll() {
        hazelcastClientNode.shutdown();
        HazelcastServerNode.shutdown();
    }

    @Test
    void testSimpleLock() {
        String result = doWithLock("pid1", new LockOperation<String>() {
            @Override
            public String execute() {
                return "pepo";
            }
        });
        assertEquals("pepo", result);
    }

    @Test
    void testReentrant() {
        String pid = "L1";
        Boolean result = doWithLock(pid, () -> {
            Boolean result1 = doWithLock(pid, () -> true);
            return result1;
        });
        assertTrue(result);
    }

    @Test
    void testLockMutualExclusion() throws Exception {
        CountDownLatch t1Acquired = new CountDownLatch(1);
        CountDownLatch t2Entered = new CountDownLatch(1);
        AtomicLong t2EnterTime = new AtomicLong();
        AtomicLong t1ReleaseTime = new AtomicLong();

        Thread t1 = new Thread(() -> {
            doWithLock("pid1", () -> {
                System.out.println("T1 acquired lock");
                t1Acquired.countDown();
                sleep(5000); // hold lock
                t1ReleaseTime.set(System.currentTimeMillis());
                System.out.println("T1 releasing lock");
                return null;
            });
        });

        Thread t2 = new Thread(() -> {
            try {
                t1Acquired.await();
                System.out.println("T2 trying lock");
                doWithLock("pid1", () -> {
                    t2EnterTime.set(System.currentTimeMillis());
                    System.out.println("T2 acquired lock");
                    t2Entered.countDown();
                    return null;
                });

            } catch (InterruptedException ignored) {
            }
        });

        long start = System.currentTimeMillis();
        t1.start();
        t2.start();
        t2Entered.await(); // wait until T2 actually gets lock
        t1.join();
        t2.join();

        long waitedMillis = t2EnterTime.get() - start;
        assertTrue(waitedMillis >= 4500, "T2 should NOT acquire lock immediately (should wait ~5 sec)");
        assertTrue(t2EnterTime.get() >= t1ReleaseTime.get(), "T2 must acquire lock only AFTER T1 released it");
    }


    // Example helper; replace with your actual implementation
    private <T> T doWithLock(String pid, LockOperation<T> operation) {
        try {
            var lock = hazelcastClientNode.getHzInstance().getLock(pid);
            if (lock.tryLock(20, java.util.concurrent.TimeUnit.SECONDS, 5, java.util.concurrent.TimeUnit.HOURS)) {
                try {
                    return operation.execute();
                } finally {
                    lock.unlock();
                }
            } else {
                throw new RuntimeException("Lock timeout");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static HazelcastConfiguration createHazelcastConfig() {
        HazelcastConfiguration hazelcastConfig = new HazelcastConfiguration.Builder()
                .hazelcastInstance("akubrasync")
                .hazelcastUser("dev")
                .build();
        return hazelcastConfig;
    }

    private static void ensureHazelcastClientNode(HazelcastConfiguration hazelcastConfig) {
        try {
            hazelcastClientNode.ensureHazelcastNode(hazelcastConfig);
        } catch (Exception e) {
            System.out.println("Hazelcast lock service could not be created:" + e.toString());
        }
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
