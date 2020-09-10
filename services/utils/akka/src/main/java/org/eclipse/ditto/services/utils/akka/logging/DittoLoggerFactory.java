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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.slf4j.LoggerFactory;

import akka.actor.Actor;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;

/**
 * Factory for obtaining instances of {@link DittoLogger} and {@link DittoDiagnosticLoggingAdapter}.
 */
@Immutable
public final class DittoLoggerFactory {

    private DittoLoggerFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a {@link DittoLogger} named corresponding to the class passed as parameter, using the statically bound
     * {@link org.slf4j.ILoggerFactory} instance.
     * <p>
     * In case the the clazz parameter differs from the name of the caller as computed internally by SLF4J, a logger
     * name mismatch warning will be printed but only if the {@code slf4j.detectLoggerNameMismatch} system property is
     * set to {@code true}.
     * By default, this property is not set and no warnings will be printed even in case of a logger name mismatch.
     * </p>
     * <p>
     * The returned logger is <em>not thread-safe!</em>.
     * </p>
     *
     * @param clazz provides the name of the returned logger.
     * @return the logger.
     * @throws NullPointerException if {@code clazz} is {@code null}.
     */
    public static DittoLogger getLogger(final Class<?> clazz) {
        return DefaultDittoLogger.of(LoggerFactory.getLogger(checkNotNull(clazz, "clazz")));
    }

    /**
     * Returns a {@link DittoLogger} named according to the name parameter using the statically bound
     * {@link org.slf4j.ILoggerFactory} instance.
     * <p>
     * The returned logger is <em>not thread-safe!</em>.
     * </p>
     *
     * @param name the name of the logger.
     * @return the not thread-safe logger.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    public static DittoLogger getLogger(final CharSequence name) {
        return DefaultDittoLogger.of(LoggerFactory.getLogger(checkNotNull(name, "name").toString()));
    }

    /**
     * Returns a {@link ThreadSafeDittoLogger} named corresponding to the class passed as parameter, using the
     * statically bound {@link org.slf4j.ILoggerFactory} instance.
     * <p>
     * In case the the clazz parameter differs from the name of the caller as computed internally by SLF4J, a logger
     * name mismatch warning will be printed but only if the {@code slf4j.detectLoggerNameMismatch} system property is
     * set to {@code true}.
     * By default, this property is not set and no warnings will be printed even in case of a logger name mismatch.
     * </p>
     *
     * @param clazz provides the name of the returned logger.
     * @return the thread-safe logger.
     * @throws NullPointerException if {@code clazz} is {@code null}.
     * @since 1.3.0
     */
    public static ThreadSafeDittoLogger getThreadSafeLogger(final Class<?> clazz) {
        return ImmutableThreadSafeDittoLogger.of(LoggerFactory.getLogger(checkNotNull(clazz, "clazz")));
    }

    /**
     * Returns a {@link ThreadSafeDittoLogger} named according to the name parameter using the statically bound
     * {@link org.slf4j.ILoggerFactory} instance.
     *
     * @param name the name of the logger.
     * @return the thread-safe logger.
     * @throws NullPointerException if {@code clazz} is {@code null}.
     * @since 1.3.0
     */
    public static ThreadSafeDittoLogger getThreadSafeLogger(final CharSequence name) {
        return ImmutableThreadSafeDittoLogger.of(LoggerFactory.getLogger(checkNotNull(name, "name").toString()));
    }

    /**
     * Returns a LoggingAdapter with MDC support for the given actor.
     *
     * @param logSource the Actor used as logSource
     * @return the not thread-safe logging adapter.
     * @throws NullPointerException if {@code logSource} is {@code null}.
     */
    public static DittoDiagnosticLoggingAdapter getDiagnosticLoggingAdapter(final Actor logSource) {
        return DefaultDittoDiagnosticLoggingAdapter.of(getPlainDiagnosticLoggingAdapter(logSource));
    }

    private static DiagnosticLoggingAdapter getPlainDiagnosticLoggingAdapter(final Actor logSource) {
        return Logging.apply(checkNotNull(logSource, "logSource"));
    }

    /**
     * Returns a {@link ThreadSafeDittoDiagnosticLoggingAdapter} with MDC support for the given actor.
     *
     * @param logSource the Actor used as logSource
     * @return the thread-safe logging adapter.
     * @throws NullPointerException if {@code logSource} is {@code null}.
     * @since 1.3.0
     */
    public static ThreadSafeDittoDiagnosticLoggingAdapter getThreadSafeDiagnosticLoggingAdapter(final Actor logSource) {
        return ImmutableDittoDiagnosticLoggingAdapter.of(getPlainDiagnosticLoggingAdapter(logSource));
    }

}
