/*
 * Copyright (C) 2025 Inovatika
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ceskaexpedice.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ILock;
import com.hazelcast.util.ThreadUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class LocksTest {

    private static HazelcastClientNode hazelcastClientNode;
    // private static DistributedLockService lockService;
    private static HazelcastConfiguration hazelcastConfig;

    @BeforeAll
    static void beforeAll() {
        hazelcastConfig = createHazelcastConfig();
        //HazelcastServerNode.ensureHazelcastNode(hazelcastConfig);

        hazelcastClientNode = new HazelcastClientNode();
        ensureHazelcastClientNode(hazelcastConfig);

    }

    @AfterAll
    static void afterAll() {
        hazelcastClientNode.shutdown();
        //HazelcastServerNode.shutdown();
    }

    @Test
    void testSimpleLock() {
        String result = doWithReadLock("1", new LockOperation<String>() {
            @Override
            public String execute() {
                return "pepo";
            }
        });
        assertEquals("pepo", result);
    }

    private static Stream<TimeUnit> leaseUnits() {
        return Stream.of(TimeUnit.HOURS, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @MethodSource("leaseUnits")
    public void testLeaseExpirationAcrossClientsRaw(TimeUnit leaseUnit) throws Exception {
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
    void rawThreadTest() throws Exception {
        HazelcastConfiguration hazelcastConfigX = new HazelcastConfiguration.Builder()
                //.hazelcastInstance("akubrasync")
                .hazelcastUser("dev")
                .build();

        HazelcastClientNode hazelcastClientNodeX = new HazelcastClientNode();
        hazelcastClientNodeX.ensureHazelcastNode(hazelcastConfigX);

        ILock lock = hazelcastClientNodeX.getHzInstance().getLock("1");

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("T1 try lock");
                boolean ok = lock.tryLock(20, TimeUnit.SECONDS, 5, TimeUnit.HOURS);
                System.out.println("T1 acquired = " + ok);

                if (ok) {
                    Thread.sleep(15000);
                    lock.unlock();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(5000);
                System.out.println("T2 try lock");
                boolean ok = lock.tryLock(20, TimeUnit.SECONDS, 5, TimeUnit.HOURS);
                System.out.println("T2 acquired = " + ok);

                if (ok) lock.unlock();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();
    }

    /*
    @Test
    void testReadLockAndWriteLock() {
        Stack<String> result = new Stack<>();
        Stack<String> msg = new Stack<>();
        ConcurrencyUtils.runFactoryTasks(2, new Function<>() {
            @Override
            public ConcurrencyUtils.TestTask apply(Integer taskNumber) {
                if (taskNumber == 1) {
                    return new ConcurrencyUtils.TestTask("first") {
                        @Override
                        public void run() {
                            super.run();
                            String pid = "1";
                            doWithReadLock(pid, () -> {
                                result.push(Thread.currentThread().getName());
                                msg.push(Thread.currentThread().getName() + ": acquiredReadLock for first");
                                sleep(15000);
                                return null;
                            });

                            System.out.println("Java thread = " + Thread.currentThread().getId() + " HZ thread = " + ThreadUtil.getThreadId());


                            msg.push(Thread.currentThread().getName() + ": releasedReadLock for first");
                            result.push(Thread.currentThread().getName());
                        }
                    };
                } else {
                    return new ConcurrencyUtils.TestTask("second") {
                        @Override
                        public void run() {
                            super.run();
                            String pid = "1";
                            sleep(5000); // make sure the first thread has time to acquire lock
                            doWithWriteLock(pid, () -> {
                                result.push(Thread.currentThread().getName());
                                msg.push(Thread.currentThread().getName() + ": acquiredWriteLock for second");
                                return null;
                            });

                            System.out.println("Java thread = " + Thread.currentThread().getId() + " HZ thread = " + ThreadUtil.getThreadId());


                            msg.push(Thread.currentThread().getName() + ": releasedWriteLock for second");
                            result.push(Thread.currentThread().getName());
                        }
                    };
                }
            }
        });
        System.out.println("---------MSG");
        System.out.println(msg);
        System.out.println("---------RESULT");
        System.out.println(result);
        String last = result.pop();
        String secondLast = result.pop();
        assertEquals(last, secondLast);
        String thirdLast = result.pop();
        String fourthLast = result.pop();
        assertEquals(thirdLast, fourthLast);
        assertTrue(result.isEmpty());
    }

     */

    private static HazelcastConfiguration createHazelcastConfig() {
        HazelcastConfiguration hazelcastConfig = new HazelcastConfiguration.Builder()
                .hazelcastInstance("akubrasync")
                .hazelcastUser("dev")
                .build();
        return hazelcastConfig;
    }


    <T> T doWithReadLock(String pid, LockOperation<T> operation) {
        return doWithLock(pid, operation, false);
    }


    <T> T doWithWriteLock(String pid, LockOperation<T> operation) {
        return doWithLock(pid, operation, true);
    }

    private final ConcurrentHashMap<String, Object> localMutexes = new ConcurrentHashMap<>();

    private Object getLocalMutex(String key) {
        return localMutexes.computeIfAbsent(key, k -> new Object());
    }

    private <T> T doWithLock(String pid, LockOperation<T> operation, boolean writeLock) {
        Object localMutex = getLocalMutex(pid);
        synchronized (localMutex) {   // JVM-level protection
            try {
                //ReadWriteLock readWriteLock = lockService.getReentrantReadWriteLock(pid);
                //Lock lock = writeLock ? readWriteLock.writeLock() : readWriteLock.readLock();
                ILock lock = hazelcastClientNode.getHzInstance().getLock(pid);


                if (lock == null) {
                    throw new RuntimeException("Null lock acquired");
                }
                if (lock.tryLock(20, TimeUnit.SECONDS, 5, TimeUnit.HOURS)) {
                    try {
                        return operation.execute();
                    } finally {
                        lock.unlock();
                    }
                } else {
                    throw new RuntimeException("Lock timed out after sec:" + 20);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void ensureHazelcastClientNode(HazelcastConfiguration hazelcastConfig) {
        try {
            hazelcastClientNode.ensureHazelcastNode(hazelcastConfig);
            //lockService = DistributedLockService.newHazelcastLockService(hazelcastClientNode, hazelcastConfig);
        } catch (Exception e) {
            System.out.println("Hazelcast lock service could not be created:" + e.toString());
        }
    }

}
