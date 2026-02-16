package org.ceskaexpedice.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ILock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HazelcastLocksTest {
    private static HazelcastClientNode hazelcastClientNode;
    private static HazelcastConfiguration hazelcastConfig;

    @BeforeAll
    static void beforeAll() {
        hazelcastConfig = createHazelcastConfig();
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
    void testSimpleLock() throws TimeoutException {
        String result = doWithLock("pid1", hazelcastConfig, hazelcastClientNode, new LockOperation<String>() {
            @Override
            public String execute() {
                return "pepo";
            }
        });
        assertEquals("pepo", result);
    }

    @Test
    void testReentrant() throws TimeoutException {
        String pid = "L1";
        Boolean result = doWithLock(pid, hazelcastConfig, hazelcastClientNode, () -> {
            Boolean result1 = doWithLock(pid, hazelcastConfig, hazelcastClientNode, () -> true);
            return result1;
        });
        assertTrue(result);
    }

    @Test
    void testMutualExclusion() throws Exception {
        CountDownLatch t1Acquired = new CountDownLatch(1);
        CountDownLatch t2Entered = new CountDownLatch(1);
        AtomicLong t2EnterTime = new AtomicLong();
        AtomicLong t1ReleaseTime = new AtomicLong();

        Thread t1 = new Thread(() -> {
            try {
                doWithLock("pid1", hazelcastConfig, hazelcastClientNode, () -> {
                    System.out.println("T1 acquired lock");
                    t1Acquired.countDown();
                    sleep(5000); // hold lock
                    t1ReleaseTime.set(System.currentTimeMillis());
                    System.out.println("T1 releasing lock");
                    return null;
                });
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                t1Acquired.await();
                System.out.println("T2 trying lock");
                try {
                    doWithLock("pid1", hazelcastConfig, hazelcastClientNode, () -> {
                        t2EnterTime.set(System.currentTimeMillis());
                        System.out.println("T2 acquired lock");
                        t2Entered.countDown();
                        return null;
                    });
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
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

    @Test
    void testLeaseExpiration() throws Exception {
        HazelcastConfiguration config = new HazelcastConfiguration.Builder()
                .hazelcastInstance("akubrasyncX")
                .hazelcastUser("dev")
                .waitTimeSecs(20L)
                .leaseTimeSecs(5L)
                .build();
        HazelcastClientNode node = new HazelcastClientNode();
        node.ensureHazelcastNode(config);

        CountDownLatch t1Acquired = new CountDownLatch(1);
        CountDownLatch t2Finished = new CountDownLatch(1);
        AtomicBoolean t2Success = new AtomicBoolean(false);

        Thread t1 = new Thread(() -> {
            try {
                ILock lock = hazelcastClientNode.getHzInstance().getLock("pidLease");
                boolean ok = lock.tryLock(
                        config.getWaitTimeSecs(),
                        TimeUnit.SECONDS,
                        config.getLeaseTimeSecs(),
                        TimeUnit.SECONDS
                );
                if (!ok) {
                    throw new RuntimeException("T1 failed to acquire lock");
                }
                System.out.println("T1 acquired lock with lease=" + config.getLeaseTimeSecs() + " sec");
                t1Acquired.countDown();
                // Simulate crash / stuck computation (no unlock)
                sleep((int) ((config.getLeaseTimeSecs() + 3) * 1000L));
                System.out.println("T1 finished WITHOUT unlock (simulate crash)");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            // NO unlock here on purpose
        });

        Thread t2 = new Thread(() -> {
            try {
                t1Acquired.await();
                // Wait until lease should be expired
                sleep((int) ((config.getLeaseTimeSecs() + 2) * 1000L));
                System.out.println("T2 trying via doWithLock after lease expiry");
                doWithLock("pidLease", config, node, () -> {
                    System.out.println("T2 acquired lock after lease expiry");
                    t2Success.set(true);
                    return null;
                });
                t2Finished.countDown();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        t1.start();
        t2.start();
        t2Finished.await();
        t1.join();
        t2.join();
        node.shutdown();

        assertTrue(t2Success.get(), "T2 must acquire lock after lease expiration");
    }

    private static Stream<TimeUnit> leaseUnits() {
        return Stream.of(TimeUnit.HOURS, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @MethodSource("leaseUnits")
    public void testLeaseExpirationRaw(TimeUnit leaseUnit) throws Exception {
        // =========================
        // First client
        // =========================
        ClientConfig config1 = new ClientConfig();
        HazelcastInstance client1 = HazelcastClient.newHazelcastClient(config1);
        ILock lock1 = client1.getLock("L1");

        System.out.println("Client1: acquiring lock with lease 5 " + leaseUnit);
        boolean acquired1 = lock1.tryLock(1, TimeUnit.SECONDS, 5, leaseUnit);
        System.out.println("Client1 acquired: " + acquired1);
        assertTrue(acquired1);

        // Simulate long work without unlocking (sleep longer than lease and (hopefully) no heartbeat from client1)
        System.out.println("Client1: sleeping 6 sec to let lease expire...");
        Thread.sleep(6000);

        // =========================
        // Second client
        // =========================
        ClientConfig config2 = new ClientConfig();
        HazelcastInstance client2 = HazelcastClient.newHazelcastClient(config2);
        ILock lock2 = client2.getLock("L1");

        System.out.println("Client2: trying to acquire the same lock...");
        boolean acquired2 = lock2.tryLock(1, TimeUnit.SECONDS, 5, TimeUnit.SECONDS);
        System.out.println("Client2 acquired: " + acquired2);
        if (leaseUnit == TimeUnit.SECONDS) {
            assertTrue(acquired2);
        } else {
            assertFalse(acquired2);
        }

        // Cleanup
        if (acquired2) lock2.unlock();
        if (acquired1) {
            if (lock1.isLockedByCurrentThread()) {
                lock1.unlock();
            }
        }
        client1.shutdown();
        client2.shutdown();
    }

    @Test
    void testDeadlock() throws Exception {
        HazelcastConfiguration config = new HazelcastConfiguration.Builder()
                .hazelcastInstance("akubrasyncX")
                .hazelcastUser("dev")
                .waitTimeSecs(10L)
                .build();
        HazelcastClientNode node = new HazelcastClientNode();
        node.ensureHazelcastNode(config);

        AtomicBoolean t1Timeout = new AtomicBoolean(false);
        AtomicBoolean t2Timeout = new AtomicBoolean(false);

        CountDownLatch t1HasWriteLock = new CountDownLatch(1);
        CountDownLatch t2HasWriteLock = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            try {
                doWithLock("PID_MONOGRAPH", config, node, () -> {
                    System.out.println(Thread.currentThread().getName() + ": acquired lock for " + "PID_MONOGRAPH");
                    t1HasWriteLock.countDown();
                    try {
                        // Wait until T2 also holds its write lock → guarantees deadlock scenario
                        t2HasWriteLock.await();
                        System.out.println(Thread.currentThread().getName() + ": attempting lock for " + "PID_TITLE_PAGE");
                        doWithLock("PID_TITLE_PAGE", config, node, () -> null);
                    } catch (TimeoutException e) {
                            t1Timeout.set(true);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }, "Deadlock-T1");

        Thread t2 = new Thread(() -> {
            try {
                doWithLock("PID_TITLE_PAGE", config, node, () -> {
                    System.out.println(Thread.currentThread().getName() + ": acquired lock for " + "PID_TITLE_PAGE");
                    t2HasWriteLock.countDown();
                    try {
                        // Wait until T1 holds write lock → guarantees circular wait
                        t1HasWriteLock.await();
                        System.out.println(Thread.currentThread().getName() + ": attempting lock for " + "PID_MONOGRAPH");
                        doWithLock("PID_MONOGRAPH", config, node, () -> null);
                    } catch (TimeoutException e) {
                            t2Timeout.set(true);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }, "Deadlock-T2");

        // ---- start ----
        t1.start();
        t2.start();

        // ---- wait completion ----
        t1.join();
        t2.join();

        // ---- asserts ----
        assertTrue(
                t1Timeout.get() || t2Timeout.get(),
                "At least one thread must detect lock timeout to break deadlock"
        );
        node.shutdown();
    }

    // Example helper; replace with your actual implementation
    private <T> T doWithLock(String lockName, HazelcastConfiguration config, HazelcastClientNode node,
                             LockOperation<T> operation) throws TimeoutException {
        try {
            var lock = node.getHzInstance().getLock(lockName);
            if (lock.tryLock(config.getWaitTimeSecs(), TimeUnit.SECONDS, config.getLeaseTimeSecs(), TimeUnit.HOURS)) {
                try {
                    return operation.execute();
                } finally {
                    lock.unlock();
                }
            } else {
                throw new TimeoutException("Could not acquire lock " + lockName + " within " + config.getWaitTimeSecs()
                        + " " + TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static HazelcastConfiguration createHazelcastConfig() {
        HazelcastConfiguration hazelcastConfig = new HazelcastConfiguration.Builder()
                .hazelcastInstance("akubrasync")
                .hazelcastUser("dev")
                .waitTimeSecs(120L)
                .leaseTimeSecs(300L)
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
