/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.slf4j.Logger;

/**
 * A SLF4J {@link Logger} with additional functionality.
 * The main purpose of ThreadSafeDittoLogger is to provide an API for easily setting a correlation ID to the logging
 * <a href="http://logback.qos.ch/manual/mdc.html">Mapped Diagnostic Context (MDC)</a> <em>before each log
 * operation</em>.
 * <p>
 * Once a usable correlation ID is set to a ThreadSafeDittoLogger it will be appended to the log by all log operations
 * of that logger instance.
 * Usable means that the correlation ID is neither {@code null} nor an empty CharSequence.
 * </p>
 * <p>
 * The following example shows that the correlation ID gets only appended for the info message.
 * The call {@code logger.withCorrelation(...)} creates a temporary logger instance which includes the specified
 * correlation ID.
 * The warn message uses the original logger without a correlation ID, therefore, the correlation ID gets not appended.
 * </p>
 * <p>
 * <pre>
 * final ThreadSafeDittoLogger logger = DittoLoggerFactory.getThreadSafeLogger(MyClass.class);
 * logger.withCorrelationId("my-correlation-id").info("This is a normal SLF4J log operation with correlation ID in MDC.");
 * logger.warn("The correlation ID is appended no more.");
 * </pre>
 * </p>
 * <p>
 * The second example shows how to use the same correlation ID for several log operations without the need to call
 * {@code withCorrelationId(...)} before each.
 * A call of {@link #discardCorrelationId()} discards the correlation ID eventually.
 * </p>
 * <p>
 * <pre>
 * ThreadSafeDittoLogger logger = DittoLoggerFactory.getThreadSafeLogger(MyClass.class); // note that the variable is not final
 * logger = logger.withCorrelationId("my-correlationId"); // assigns a new logger instance to "logger" variable.
 * logger.info("This message has the appended correlation ID.");
 * logger.info("So does this message.");
 * logger.info("And this.");
 * logger = logger.discardCorrelationId(); // assigns a new logger instance without correlation ID to "logger" variable.
 * logger.warn("The correlation ID is appended no more.");
 * </pre>
 * </p>
 * <p>
 * Objects of this class are safe to be shared between different threads.
 * </p>
 *
 * @since 1.4.0
 */
@ThreadSafe
public interface ThreadSafeDittoLogger extends Logger, WithMdcEntry<ThreadSafeDittoLogger> {

    /**
     * Sets the given correlation ID for log operations on the returned logger.
     * Setting {@code null} or an empty CharSequence has the same effect as a call to {@link #discardCorrelationId()}.
     *
     * @param correlationId the correlation ID to be put to the MDC before each log operation.
     * @return a ThreadSafeDittoLogger which appends the specified correlation ID to all of its log operations.
     */
    ThreadSafeDittoLogger withCorrelationId(@Nullable CharSequence correlationId);

    /**
     * Derives the correlation ID from the given WithDittoHeaders for the log operations on the returned logger.
     * If no or an empty correlation ID can be derived, this method has the same effect like
     * {@link #discardCorrelationId()}.
     *
     * @param withDittoHeaders provides DittoHeaders which might contain the correlation ID to be put to the MDC before
     * each log operation.
     * @return a ThreadSafeDittoLogger which appends the derived correlation ID to all of its log operations.
     * @see #withCorrelationId(DittoHeaders)
     */
    ThreadSafeDittoLogger withCorrelationId(@Nullable WithDittoHeaders withDittoHeaders);

    /**
     * Obtains the correlation ID from the given DittoHeaders for log operations on the returned logger.
     * If the given headers do not contain a correlation ID or if the correlation ID is empty, this method has the same
     * effect like {@link #discardCorrelationId()}.
     *
     * @param dittoHeaders might contain the correlation ID to be put to the MDC before each log operation.
     * @return a ThreadSafeDittoLogger which appends the obtained correlation ID to all of its log operations.
     */
    ThreadSafeDittoLogger withCorrelationId(@Nullable DittoHeaders dittoHeaders);

    /**
     * Removes the currently set correlation ID from the MDC.
     * Log operations on the returned logger will not include a correlation ID.
     *
     * @return a ThreadSafeDittoLogger without a correlation ID.
     */
    ThreadSafeDittoLogger discardCorrelationId();

}
