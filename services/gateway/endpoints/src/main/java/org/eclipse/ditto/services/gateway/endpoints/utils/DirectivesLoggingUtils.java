/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.slf4j.MDC;

/**
 * Utility class which provides wrappers for adding a setting a correlation id to slf4j's {@link org.slf4j.MDC} and
 * restoring the original {@link org.slf4j.MDC} when the wrapped call is finished.
 */
public final class DirectivesLoggingUtils {

    private static final String X_CR_CORRELATION_ID = HttpHeader.X_CORRELATION_ID.getName();

    private DirectivesLoggingUtils() {
        throw new AssertionError();
    }

    /**
     * Enhances the passed {@link Supplier} with an "MDC" map entry for the passed {@code correlationId}.
     *
     * @param correlationId the correlation ID to set.
     * @param supplier the Supplier to set the "MDC" on.
     */
    public static <T> T enhanceLogWithCorrelationId(final CharSequence correlationId, final Supplier<T> supplier) {
        return new CorrelationIdLoggingSupplier<>(correlationId, supplier).get();
    }

    /**
     * Enhances the passed {@link Runnable} with an "MDC" map entry for the passed {@code correlationId}.
     *
     * @param correlationId the correlation ID to set.
     * @param runnable the Runnable to set the "MDC" on.
     */
    public static void enhanceLogWithCorrelationId(final CharSequence correlationId, final Runnable runnable) {
        new CorrelationIdLoggingSupplier<Void>(correlationId, () -> {
            runnable.run();
            return null;
        }).get();
    }

    /**
     * Enhances the passed {@link Supplier} with an "MDC" map entry for the passed {@code correlationIdOpt}, if it is
     * present.
     *
     * @param correlationIdOpt the optional correlationId to set.
     * @param supplier the Supplier to set the "MDC" on.
     */
    public static <T> T enhanceLogWithCorrelationId(final Optional<String> correlationIdOpt,
            final Supplier<T> supplier) {

        return correlationIdOpt.map(correlationId -> enhanceLogWithCorrelationId(correlationId, supplier))
                .orElse(supplier.get());
    }

    /**
     * Enhances the passed {@link Runnable} with an "MDC" map entry for the passed {@code correlationIdOpt}, if it is
     * present.
     *
     * @param correlationIdOpt the optional correlationId to set.
     * @param runnable the Runnable to set the "MDC" on.
     */
    public static void enhanceLogWithCorrelationId(final Optional<String> correlationIdOpt, final Runnable runnable) {
        if (correlationIdOpt.isPresent()) {
            enhanceLogWithCorrelationId(correlationIdOpt.get(), runnable);
        } else {
            runnable.run();
        }
    }

    private static final class CorrelationIdLoggingSupplier<T> implements Supplier<T> {

        private final String correlationId;
        private final Supplier<T> wrapped;

        private CorrelationIdLoggingSupplier(final CharSequence correlationId, final Supplier<T> wrapped) {
            this.correlationId = requireNonNull(correlationId).toString();
            this.wrapped = requireNonNull(wrapped);
        }

        @Override
        public T get() {
            final Map<String, String> originalContext = MDC.getCopyOfContextMap();
            MDC.put(X_CR_CORRELATION_ID, correlationId);
            try {
                return wrapped.get();
            } finally {
                if (originalContext != null) {
                    MDC.setContextMap(originalContext);
                } else {
                    MDC.clear();
                }
            }
        }

    }

}
