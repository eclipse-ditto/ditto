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
package org.eclipse.ditto.services.utils.akka.logging;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;

/**
 * This interface represents a SLF4J {@link Logger} which is {@link AutoCloseable}.
 * The semantic of the {@link #close()} method is defined by its implementation.
 * Additionally the logger is aware of the correlation ID concept.
 * I. e. users may provide a correlation ID which then is put to the MDC to enhance log statements.
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

    @Override
    void close();

}
