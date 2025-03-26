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
import org.ceskaexpedice.test.ConcurrencyUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class LocksTest {

    private static HazelcastClientNode hazelcastClientNode;
    private static DistributedLockService lockService;

    @BeforeAll
    static void beforeAll() {
        HazelcastConfiguration hazelcastConfig = createHazelcastConfig();
        HazelcastServerNode.ensureHazelcastNode(hazelcastConfig);

        hazelcastClientNode = new HazelcastClientNode();
        hazelcastClientNode.ensureHazelcastNode(hazelcastConfig);
        lockService = DistributedLockService.newHazelcastLockService(hazelcastClientNode);
    }

    @AfterAll
    static void afterAll() {
        lockService.shutdown();
        hazelcastClientNode.shutdown();
        HazelcastServerNode.shutdown();
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

    private Lock getWriteLock(String pid) {
        if (pid == null) {
            throw new IllegalArgumentException("pid cannot be null");
        }
        ReadWriteLock lock = lockService.getReentrantReadWriteLock(pid);
        lock.writeLock().lock();
        return lock.writeLock();
    }

    private Lock getReadLock(String pid) {
        if (pid == null) {
            throw new IllegalArgumentException("pid cannot be null");
        }
        ReadWriteLock lock = lockService.getReentrantReadWriteLock(pid);
        lock.readLock().lock();
        return lock.readLock();
    }

    private <T> T doWithReadLock(String pid, LockOperation<T> operation) {
        Lock readLock = getReadLock(pid);
        try {
            return operation.execute();
        } finally {
            readLock.unlock();
        }
    }

    private <T> T doWithWriteLock(String pid, LockOperation<T> operation) {
        Lock writeLock = getWriteLock(pid);
        try {
            return operation.execute();
        } finally {
            writeLock.unlock();
        }
    }

    private static void sleep(int millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
