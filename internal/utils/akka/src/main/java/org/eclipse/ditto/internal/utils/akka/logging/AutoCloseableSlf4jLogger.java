/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.internal.utils.akka.logging;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;

/**
 * This interface represents a SLF4J {@link Logger} which is {@link AutoCloseable}.
 * The semantic of the {@link #close()} method is defined by its implementation.
 * Additionally this logger maintains its own local MDC which is applied to the general MDC for each log operation.
 * For example, users may provide a correlation ID which then is put to the MDC to enhance log statements.
 */
@NotThreadSafe
public interface AutoCloseableSlf4jLogger extends Logger, AutoCloseable {

    /**
     * Sets the given correlation ID for log operations until it gets discarded.
     *
     * @param correlationId the correlation ID to be put to the MDC.
     * @return this logger instance to allow method chaining.
     */
    AutoCloseableSlf4jLogger setCorrelationId(@Nullable CharSequence correlationId);

    /**
     * Discards a previously set correlation ID and removes it from the MDC.
     */
    void discardCorrelationId();

    /**
     * Puts the specified diagnostic context value as identified by the specified key to this logger's MDC until it gets
     * removed.
     * <p>
     * Providing {@code null} as value has the same effect as calling {@link #removeMdcEntry(CharSequence)} with the
     * specified key.
     * </p>
     *
     * @param key the key which identifies the diagnostic value.
     * @param value the diagnostic value which is identified by {@code key}.
     * @return this or a new logger instance for method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     * @see #removeMdcEntry(CharSequence)
     * @since 1.4.0
     */
    AutoCloseableSlf4jLogger putMdcEntry(CharSequence key, @Nullable CharSequence value);

    /**
     * Removes from the MDC the diagnostic context value identified by the specified key.
     * This method does nothing if there is no previous value associated with the specified key.
     *
     * @param key the key of the value to be removed.
     * @return this or a new logger instance for method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     * @since 1.4.0
     */
    AutoCloseableSlf4jLogger removeMdcEntry(CharSequence key);

    @Override
    void close();

}
