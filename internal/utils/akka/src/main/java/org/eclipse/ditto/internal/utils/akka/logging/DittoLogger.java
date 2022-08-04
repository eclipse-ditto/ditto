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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.slf4j.Logger;

/**
 * <p>
 * A SLF4J {@link Logger} with additional functionality.
 * The main purpose of DittoLogger is to provide an API for easily setting a correlation ID to the logging
 * <a href="http://logback.qos.ch/manual/mdc.html">Mapped Diagnostic Context (MDC)</a>.
 * </p>
 *
 * <p>
 * DittoLogger can be used in two ways:
 * <ol>
 *     <li>set the correlation ID per log operation, i. e. it gets automatically discarded immediately after having
 *     performed the log operation.</li>
 *     <li>Or set the correlation ID globally until it gets manually discarded.</li>
 * </ol>
 * </p>
 *
 * <p>
 * The following example shows usage for case 1.
 * The correlation ID is only logged for the info message and gets discarded automatically afterwards.
 * <pre>
 * DittoLogger dittoLogger = DittoLoggerFactory.getLogger(MyClass.class);
 * dittoLogger.withCorrelationId("my-correlation-id").info("This is a normal SLF4J log operation.");
 * dittoLogger.warn("The correlation ID is logged no more.");
 * </pre>
 * </p>
 *
 * <p>
 * For case 2 the correlation ID is set globally and will be logged until it gets discarded manually.
 * The preferred way to do this is to utilize Javas <em>try-with-resources:</em>
 * <pre>
 * DittoLogger dittoLogger = DittoLoggerFactory.getLogger(MyClass.class);
 * try (final AutoCloseableSlf4jLogger l = dittoLogger.setCorrelationId("my-correlation-id") {
 *     l.info("This message logs the correlation ID.");
 *     l.info("So does this message.");
 *     l.info("And this.");
 *     // All log messages within this scope will contain the correlation ID.
 * }
 * dittoLogger.warn("The correlation ID is logged no more.");
 * </pre>
 * </p>
 *
 * <p>
 * The second example for case 2 requires you to call {@link #discardCorrelationId()} explicitly.
 * This approach is more flexible as it allows to set the correlation ID for a broader scope than the
 * try-with-resources way.
 * <pre>
 * DittoLogger dittoLogger = DittoLoggerFactory.getLogger(MyClass.class);
 *
 * dittoLogger.setCorrelationId("my-correlationId"); // Ignore the returned value
 * dittoLogger.info("This message logs the correlation ID.");
 * dittoLogger.info("So does this message.");
 * dittoLogger.info("And this.");
 * dittoLogger.discardCorrelationId();
 *
 * dittoLogger.warn("The correlation ID is logged no more.");
 * </pre>
 * </p>
 *
 * <p>
 * TLDR; the context values which are set via methods whose name starts with {@code with}, are automatically
 * discarded after the subsequent log operation.
 * Context values which are set via {@code set} or {@code put} methods have to be discarded or removed manually.
 * </p>
 */
@NotThreadSafe
public interface DittoLogger extends Logger, WithMdcEntry<DittoLogger> {

    /**
     * Sets the given correlation ID for the subsequent log operation.
     *
     * @param correlationId the correlation ID to be put to the MDC.
     * @return this DittoLogger instance to allow method chaining.
     */
    DittoLogger withCorrelationId(@Nullable CharSequence correlationId);

    /**
     * Derives the correlation ID from the given WithDittoHeaders for the subsequent log operation.
     *
     * @param withDittoHeaders provides DittoHeaders which might contain the correlation ID to be put to the MDC.
     * @return this DittoLogger instance to allow method chaining.
     */
    DittoLogger withCorrelationId(@Nullable WithDittoHeaders withDittoHeaders);

    /**
     * Obtains the correlation ID from the given DittoHeaders for the subsequent log operation.
     *
     * @param dittoHeaders might contain the correlation ID to be put to the MDC.
     * @return this DittoLogger instance to allow method chaining.
     */
    DittoLogger withCorrelationId(@Nullable DittoHeaders dittoHeaders);

    /**
     * Sets the given correlation ID for all subsequent log operations until it gets manually discarded.
     *
     * @param correlationId the correlation ID to be put to the MDC.
     * @return this DittoLogger instance to allow method chaining.
     */
    AutoCloseableSlf4jLogger setCorrelationId(@Nullable CharSequence correlationId);

    /**
     * Derives the correlation ID from the given WithDittoHeaders for all subsequent log operations until it gets
     * manually discarded.
     *
     * @param withDittoHeaders provides DittoHeaders which might contain the correlation ID to be put to the MDC.
     * @return this DittoLogger instance to allow method chaining.
     * @throws NullPointerException if {@code withDittoHeaders} is {@code null}.
     */
    AutoCloseableSlf4jLogger setCorrelationId(WithDittoHeaders withDittoHeaders);

    /**
     * Obtains the correlation ID from the given DittoHeaders for all subsequent log operations until it gets manually
     * discarded.
     *
     * @param dittoHeaders might contain the correlation ID to be put to the MDC.
     * @return this DittoLogger instance to allow method chaining.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    AutoCloseableSlf4jLogger setCorrelationId(DittoHeaders dittoHeaders);

    /**
     * Removes the currently set correlation ID from the MDC.
     */
    void discardCorrelationId();

}
