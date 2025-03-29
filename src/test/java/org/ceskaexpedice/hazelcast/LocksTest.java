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

import ca.thoughtwire.lock.DistributedLockService;
import com.hazelcast.client.HazelcastClientNotActiveException;
import org.ceskaexpedice.test.ConcurrencyUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class LocksTest {

    private static HazelcastClientNode hazelcastClientNode;
    private static DistributedLockService lockService;
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
        if (lockService != null) {
            lockService.shutdown();
            hazelcastClientNode.shutdown();
        }
        HazelcastServerNode.shutdown();
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

    @Test
    void testReadLockAndWriteLock() {
        Stack<String> result = new Stack<>();
        ConcurrencyUtils.runFactoryTasks(2, new Function<>() {
            @Override
            public ConcurrencyUtils.TestTask apply(Integer taskNumber) {
                if (taskNumber == 1) {
                    return new ConcurrencyUtils.TestTask(taskNumber + "") {
                        @Override
                        public void run() {
                            super.run();
                            String pid = "1";
                            doWithReadLock(pid, () -> {
                                result.push(Thread.currentThread().getName());
                                System.out.println(Thread.currentThread().getName() + ": acquiredReadLock for " + pid);
                                sleep(2000);
                                return null;
                            });
                            System.out.println(Thread.currentThread().getName() + ": releasedReadLock for " + pid);
                            result.push(Thread.currentThread().getName());
                        }
                    };
                } else {
                    return new ConcurrencyUtils.TestTask(taskNumber + "") {
                        @Override
                        public void run() {
                            super.run();
                            String pid = "1";
                            sleep(1000); // make sure the first thread has time to acquire lock
                            doWithWriteLock(pid, () -> {
                                result.push(Thread.currentThread().getName());
                                System.out.println(Thread.currentThread().getName() + ": acquiredWriteLock for " + pid);
                                return null;
                            });
                            System.out.println(Thread.currentThread().getName() + ": releasedWriteLock for " + pid);
                            result.push(Thread.currentThread().getName());
                        }
                    };
                }
            }
        });
        String last = result.pop();
        String secondLast = result.pop();
        assertEquals(last, secondLast);
        String thirdLast = result.pop();
        String fourthLast = result.pop();
        assertEquals(thirdLast, fourthLast);
        assertTrue(result.isEmpty());
    }

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

    private <T> T doWithLock(String pid, LockOperation<T> operation, boolean writeLock) {
        try {
            ReadWriteLock readWriteLock = lockService.getReentrantReadWriteLock(pid);
            Lock lock = writeLock ? readWriteLock.writeLock() : readWriteLock.readLock();
            if (lock == null) {
                throw new RuntimeException("Null lock acquired");
            }
            if (lock.tryLock(20, TimeUnit.SECONDS)) {
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
            lockService = DistributedLockService.newHazelcastLockService(hazelcastClientNode);
        } catch (Exception e) {
            System.out.println("Hazelcast lock service could not be created:" + e.toString());
            //throw new RuntimeException("UAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", e);
        }
    }

}
