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

/**
 * Represents an operation that should be executed within a lock.
 * <p>
 * This interface is typically used with repository methods that apply
 * read or write locks to ensure thread-safe execution of operations.
 * </p>
 *
 * <p>Implementations define the actual logic to be executed within
 * the locking mechanism.</p>
 *
 * @param <T> The type of the result returned by the operation.
 *
 * @author pavels, petrp
 */
public interface LockOperation<T> {

    /**
     * Executes the locked operation.
     *
     * @return The result of the operation.
     */
    T execute();

}

