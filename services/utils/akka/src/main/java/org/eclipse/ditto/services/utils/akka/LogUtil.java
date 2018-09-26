/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.akka;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.slf4j.Logger;
import org.slf4j.MDC;

import akka.actor.Actor;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;

/**
 * Utilities for logging.
 */
public final class LogUtil {

    /**
     * Name of the Header for the global Ditto correlation id.
     */
    public static final String X_CORRELATION_ID = "x-correlation-id";

    private static Integer akkaClusterInstanceIndex;

    /*
     * Inhibit instantiation of this utility class.
     */
    private LogUtil() {
        // no-op
    }

    /**
     * Obtain LoggingAdapter with MDC support for the given actor.
     *
     * @param logSource the Actor used as logSource
     * @return the created DiagnosticLoggingAdapter.
     */
    public static DiagnosticLoggingAdapter obtain(final Actor logSource) {
        return Logging.apply(logSource);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id} extracted from the passed
     * {@code withDittoHeaders}. Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param withDittoHeaders where to extract a possible correlationId from.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger, final WithDittoHeaders<?> withDittoHeaders, 
            final Consumer<Logger> logConsumer) {
        logWithCorrelationId(slf4jLogger, withDittoHeaders.getDittoHeaders(), logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id} extracted from the passed
     * {@code withDittoHeaders}. Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param withDittoHeaders where to extract a possible correlationId from.
     * @param additionalMdcFields additional fields which should bet set to the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger, final WithDittoHeaders<?> withDittoHeaders,
            final Map<String, String> additionalMdcFields, final Consumer<Logger> logConsumer) {
        logWithCorrelationId(slf4jLogger, withDittoHeaders.getDittoHeaders(), additionalMdcFields, logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id} extracted from the passed
     * {@code dittoHeaders}. Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param dittoHeaders where to extract a possible correlationId from.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger, final DittoHeaders dittoHeaders,
            final Consumer<Logger> logConsumer) {
        logWithCorrelationId(slf4jLogger, dittoHeaders.getCorrelationId(), logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id} extracted from the passed
     * {@code dittoHeaders}. Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param dittoHeaders where to extract a possible correlationId from.
     * @param additionalMdcFields additional fields which should bet set to the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger, final DittoHeaders dittoHeaders,
            final Map<String, String> additionalMdcFields, final Consumer<Logger> logConsumer) {
        logWithCorrelationId(slf4jLogger, dittoHeaders.getCorrelationId(), additionalMdcFields, logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id}.
     * Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param correlationId the correlationId to put onto the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger, final Optional<String> correlationId,
            final Consumer<Logger> logConsumer) {
        logWithCorrelationId(slf4jLogger, correlationId.orElse(null), logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id}.
     * Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param correlationId the correlationId to put onto the MDC.
     * @param additionalMdcFields additional fields which should bet set to the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger, final Optional<String> correlationId,
            final Map<String, String> additionalMdcFields, final Consumer<Logger> logConsumer) {
        logWithCorrelationId(slf4jLogger, correlationId.orElse(null), additionalMdcFields, logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id}.
     * Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param correlationId the correlationId to put onto the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger, @Nullable final String correlationId,
            final Consumer<Logger> logConsumer) {
        logWithCorrelationId(slf4jLogger, correlationId, Collections.emptyMap(), logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id}.
     * Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param correlationId the correlationId to put onto the MDC.
     * @param additionalMdcFields additional fields which should bet set to the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger, @Nullable final String correlationId,
            final Map<String, String> additionalMdcFields, final Consumer<Logger> logConsumer) {
        final Map<String, String> originalContext = MDC.getCopyOfContextMap();
        if (correlationId != null) {
            MDC.put(X_CORRELATION_ID, correlationId);
        }
        additionalMdcFields.forEach(MDC::put);
        try {
            logConsumer.accept(slf4jLogger);
        } finally {
            if (originalContext != null) {
                MDC.setContextMap(originalContext);
            } else {
                MDC.clear();
            }
        }
    }

    /**
     * Enhances the passed {@link DiagnosticLoggingAdapter} with an "MDC" map entry by extracting a
     * {@code correlationId} of the passed {@code withDittoHeaders} (if present).
     *
     * @param loggingAdapter the DiagnosticLoggingAdapter to set the "MDC" on.
     * @param withDittoHeaders where to extract a possible correlationId from.
     */
    public static void enhanceLogWithCorrelationId(final DiagnosticLoggingAdapter loggingAdapter,
            final WithDittoHeaders<?> withDittoHeaders) {
        loggingAdapter.clearMDC();
        withDittoHeaders.getDittoHeaders()
                .getCorrelationId()
                .ifPresent(s -> enhanceLogWithCorrelationId(loggingAdapter, s));
    }

    /**
     * Enhances the passed {@link DiagnosticLoggingAdapter} with an "MDC" map entry by extracting a
     * {@code correlationId} of the passed {@code dittoHeaders} (if present).
     *
     * @param loggingAdapter the DiagnosticLoggingAdapter to set the "MDC" on.
     * @param dittoHeaders where to extract a possible correlationId from.
     */
    public static void enhanceLogWithCorrelationId(final DiagnosticLoggingAdapter loggingAdapter,
            final DittoHeaders dittoHeaders) {
        loggingAdapter.clearMDC();
        dittoHeaders
                .getCorrelationId()
                .ifPresent(s -> enhanceLogWithCorrelationId(loggingAdapter, s));
    }

    /**
     * Enhances the passed {@link DiagnosticLoggingAdapter} with an "MDC" map entry for the passed {@code correlationId}
     * (if present).
     *
     * @param loggingAdapter the DiagnosticLoggingAdapter to set the "MDC" on.
     * @param correlationId the optional correlationId to set.
     */
    public static void enhanceLogWithCorrelationId(final DiagnosticLoggingAdapter loggingAdapter,
            final Optional<String> correlationId) {
        loggingAdapter.clearMDC();
        correlationId.ifPresent(s -> enhanceLogWithCorrelationId(loggingAdapter, s));
    }

    /**
     * Enhances the passed {@link DiagnosticLoggingAdapter} with an "MDC" map entry for the passed {@code correlationId}
     * (if present).
     *
     * @param loggingAdapter the DiagnosticLoggingAdapter to set the "MDC" on.
     * @param correlationId the optional correlationId to set.
     */
    public static void enhanceLogWithCorrelationId(final DiagnosticLoggingAdapter loggingAdapter,
            final String correlationId) {
        loggingAdapter.clearMDC();
        if (correlationId != null && !correlationId.isEmpty()) {
            final Map<String, Object> mdcMap = new HashMap<>();
            mdcMap.put(X_CORRELATION_ID, correlationId);
            loggingAdapter.setMDC(mdcMap);
        }
    }

    /**
     * Enhances the passed {@link DiagnosticLoggingAdapter} with an "MDC" map entry for the passed {@code fieldName}
     * with the passed {@code fieldValue} (if present).
     *
     * @param loggingAdapter the DiagnosticLoggingAdapter to set the "MDC" on.
     * @param fieldName the field value to set in MDC.
     * @param fieldValue the optional value to set.
     */
    public static void enhanceLogWithCustomField(final DiagnosticLoggingAdapter loggingAdapter,
            final String fieldName, @Nullable final String fieldValue) {
        if (fieldValue != null && !fieldValue.isEmpty()) {
            final Map<String, Object> mdcMap = new HashMap<>(loggingAdapter.getMDC());
            mdcMap.put(fieldName, fieldValue);
            loggingAdapter.setMDC(mdcMap);
        }
    }

}
