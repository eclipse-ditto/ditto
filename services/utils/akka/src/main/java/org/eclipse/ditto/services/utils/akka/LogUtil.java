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
package org.eclipse.ditto.services.utils.akka;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

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
     * Name of the Header for the global Ditto correlation ID.
     */
    public static final String X_CORRELATION_ID = "x-correlation-id";

    /*
     * Inhibit instantiation of this utility class.
     */
    private LogUtil() {
        throw new AssertionError();
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
     * @param withDittoHeaders where to extract a possible correlation ID from.
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
     * @param withDittoHeaders where to extract a possible correlation ID from.
     * @param additionalMdcFields additional fields which should bet set to the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger,
            final WithDittoHeaders<?> withDittoHeaders,
            final Map<String, String> additionalMdcFields,
            final Consumer<Logger> logConsumer) {

        logWithCorrelationId(slf4jLogger, withDittoHeaders.getDittoHeaders(), additionalMdcFields, logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id} extracted from the passed
     * {@code dittoHeaders}. Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param dittoHeaders where to extract a possible correlation ID from.
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
     * @param dittoHeaders where to extract a possible correlation ID from.
     * @param additionalMdcFields additional fields which should bet set to the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger,
            final DittoHeaders dittoHeaders,
            final Map<String, String> additionalMdcFields,
            final Consumer<Logger> logConsumer) {

        logWithCorrelationId(slf4jLogger, dittoHeaders.getCorrelationId(), additionalMdcFields, logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id}.
     * Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param correlationId the correlation ID to put onto the MDC.
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
     * @param correlationId the correlation ID to put onto the MDC.
     * @param additionalMdcFields additional fields which should bet set to the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger,
            final Optional<String> correlationId,
            final Map<String, String> additionalMdcFields,
            final Consumer<Logger> logConsumer) {

        logWithCorrelationId(slf4jLogger, correlationId.orElse(null), additionalMdcFields, logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id}.
     * Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *
     * @param slf4jLogger the SLF4J logger to log with.
     * @param correlationId the correlation ID to put onto the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger, @Nullable final CharSequence correlationId,
            final Consumer<Logger> logConsumer) {

        logWithCorrelationId(slf4jLogger, correlationId, Collections.emptyMap(), logConsumer);
    }

    /**
     * Enhances the MDC around the passed SLF4J {@code Logger} with the {@code correlation-id}.
     * Invokes the passed in {@code logConsumer} around the MDC setting/removing.
     *  @param slf4jLogger the SLF4J logger to log with.
     * @param correlationId the correlation ID to put onto the MDC.
     * @param additionalMdcFields additional fields which should bet set to the MDC.
     * @param logConsumer the consumer with which the caller must log.
     */
    public static void logWithCorrelationId(final Logger slf4jLogger,
            @Nullable final CharSequence correlationId,
            final Map<String, String> additionalMdcFields,
            final Consumer<Logger> logConsumer) {

        final Map<String, String> originalContext = MDC.getCopyOfContextMap();
        enhanceLogWithCorrelationId(correlationId);

        additionalMdcFields.forEach(MDC::put);
        try {
            logConsumer.accept(slf4jLogger);
        } finally {
            if (null != originalContext) {
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
     * @param withDittoHeaders where to extract a possible correlation ID from.
     * @param additionalMdcFields additional fields to add to the MDC.
     */
    public static void enhanceLogWithCorrelationId(final DiagnosticLoggingAdapter loggingAdapter,
            final WithDittoHeaders<?> withDittoHeaders, final MdcField... additionalMdcFields) {

        enhanceLogWithCorrelationId(loggingAdapter, withDittoHeaders.getDittoHeaders().getCorrelationId(), additionalMdcFields);
    }

    /**
     * Enhances the passed {@link DiagnosticLoggingAdapter} with an "MDC" map entry by extracting a
     * {@code correlationId} of the passed {@code dittoHeaders} (if present).
     *
     * @param loggingAdapter the DiagnosticLoggingAdapter to set the "MDC" on.
     * @param dittoHeaders where to extract a possible correlation ID from.
     * @param additionalMdcFields additional fields to add to the MDC.
     */
    public static void enhanceLogWithCorrelationId(final DiagnosticLoggingAdapter loggingAdapter,
            final DittoHeaders dittoHeaders, final MdcField... additionalMdcFields) {

        enhanceLogWithCorrelationId(loggingAdapter,
        dittoHeaders.getCorrelationId(), additionalMdcFields);
    }

    /**
     * Enhances the passed {@link DiagnosticLoggingAdapter} with an "MDC" map entry for the passed {@code correlationId}
     * (if present).
     *
     * @param loggingAdapter the DiagnosticLoggingAdapter to set the "MDC" on.
     * @param correlationId the optional correlation ID to set.
     * @param additionalMdcFields additional fields to add to the MDC.
     */
    public static void enhanceLogWithCorrelationId(final DiagnosticLoggingAdapter loggingAdapter,
            final Optional<String> correlationId, final MdcField... additionalMdcFields) {
        if (correlationId.isPresent()) {
            enhanceLogWithCorrelationId(loggingAdapter, correlationId.get(), additionalMdcFields);
        } else {
            removeCorrelationId(loggingAdapter);
        }
    }

    /**
     * Enhances the passed {@link DiagnosticLoggingAdapter} with an "MDC" map entry for the passed {@code correlationId}
     * (if present).
     *
     * @param loggingAdapter the DiagnosticLoggingAdapter to set the "MDC" on.
     * @param correlationId the optional correlation ID to set.
     * @param additionalMdcFields additional fields to add to the MDC.
     */
    public static void enhanceLogWithCorrelationId(final DiagnosticLoggingAdapter loggingAdapter,
            final String correlationId, final MdcField... additionalMdcFields) {
        enhanceLogWithCustomField(loggingAdapter, X_CORRELATION_ID, correlationId, additionalMdcFields);
    }

    /**
     * Enhances the passed {@link DiagnosticLoggingAdapter} with an "MDC" map entry for the passed {@code fieldName}
     * with the passed {@code fieldValue} (if present).
     *
     * @param loggingAdapter the DiagnosticLoggingAdapter to set the "MDC" on.
     * @param fieldName the field value to set in MDC.
     * @param fieldValue the optional value to set.
     * @param additionalMdcFields additional fields to add to the MDC.
     */
    public static void enhanceLogWithCustomField(final DiagnosticLoggingAdapter loggingAdapter,
            final String fieldName, @Nullable final String fieldValue, final MdcField... additionalMdcFields) {

        final Map<String, Object> mdcMap = getMDC(loggingAdapter);

        enhanceMdcWithAdditionalField(mdcMap, fieldName, fieldValue);
        enhanceMdcWithAdditionalFields(mdcMap, additionalMdcFields);

        loggingAdapter.setMDC(mdcMap);
    }

    private static void enhanceMdcWithAdditionalFields(final Map<String, Object> mdc, final MdcField... additionalMdcFields) {
        Arrays.stream(additionalMdcFields)
                .forEach(field -> enhanceMdcWithAdditionalField(mdc, field.getName(), field.getValue()));
    }

    private static void enhanceMdcWithAdditionalField(final Map<String, Object> mdc, final String fieldName, @Nullable final String fieldValue) {
        if (fieldValue != null && !fieldValue.isEmpty()) {
            mdc.put(fieldName, fieldValue);
        } else {
            mdc.remove(fieldName);
        }
    }

    /**
     * Enhances the default slf4j {@link org.slf4j.MDC} with a {@code correlationId} of the passed
     * {@code withDittoHeaders} (if present).
     *
     * @param withDittoHeaders where to extract a possible correlation ID from.
     */
    public static void enhanceLogWithCorrelationId(final WithDittoHeaders<?> withDittoHeaders) {
        enhanceLogWithCorrelationId(withDittoHeaders.getDittoHeaders().getCorrelationId());
    }

    /**
     * Enhances the default slf4j {@link org.slf4j.MDC} with a {@code correlationId} of the passed
     * {@code dittoHeaders} (if present).
     *
     * @param dittoHeaders where to extract a possible correlation ID from.
     */
    public static void enhanceLogWithCorrelationId(final DittoHeaders dittoHeaders) {
        enhanceLogWithCorrelationId(dittoHeaders.getCorrelationId());
    }

    /**
     * Enhances the default slf4j {@link org.slf4j.MDC} with a {@code correlationId} of the passed
     * {@code withDittoHeaders} (if present), else a random correlation ID.
     *
     * @param withDittoHeaders where to extract a possible correlation ID from.
     */
    public static void enhanceLogWithCorrelationIdOrRandom(final WithDittoHeaders<?> withDittoHeaders) {
        LogUtil.enhanceLogWithCorrelationIdOrRandom(withDittoHeaders.getDittoHeaders());
    }

    /**
     * Enhances the default slf4j {@link org.slf4j.MDC} with a {@code correlationId} of the passed
     * {@code dittoHeaders} (if present), else a random correlation ID.
     *
     * @param dittoHeaders where to extract a possible correlation ID from.
     */
    public static void enhanceLogWithCorrelationIdOrRandom(final DittoHeaders dittoHeaders) {
        LogUtil.enhanceLogWithCorrelationId(
                dittoHeaders.getCorrelationId().orElseGet(() -> UUID.randomUUID().toString()));
    }

    /**
     * Enhances the default slf4j {@link org.slf4j.MDC} with a {@code correlationId}.
     *
     * @param correlationId the optional correlation ID to set.
     */
    public static void enhanceLogWithCorrelationId(final Optional<String> correlationId) {
        if (correlationId.isPresent()) {
            enhanceLogWithCorrelationId(correlationId.get());
        } else {
            removeCorrelationId();
        }
    }

    /**
     * Enhances the default slf4j {@link org.slf4j.MDC} with a {@code correlationId}.
     *
     * @param correlationId the optional correlation ID to set.
     */
    public static void enhanceLogWithCorrelationId(@Nullable final String correlationId) {
        if (correlationId != null && !correlationId.isEmpty()) {
            MDC.put(X_CORRELATION_ID, correlationId);
        } else {
            removeCorrelationId();
        }
    }

    /**
     * Gets the {@code correlationId} from the default slf4j {@link org.slf4j.MDC}.
     *
     * @return the {@code correlationId} from {@link org.slf4j.MDC} or an empty {@link java.util.Optional} if it didn't exist.
     */
    public static Optional<String> getCorrelationId() {
        final String correlationId = MDC.get(X_CORRELATION_ID);
        if (null != correlationId) {
            return Optional.of(correlationId);
        }
        return Optional.empty();
    }

    /**
     * Gets the {@code correlationId} from the default slf4j {@link org.slf4j.MDC}.
     *
     * @param defaultCorrelationIdSupplier supplies a default correlation ID if none could be found inside {@link org.slf4j.MDC}.
     * @return The {@code correlationId} from {@link org.slf4j.MDC} or from {@code defaultCorrelationIdSupplier} if it didn't exist.
     */
    public static String getCorrelationId(final Supplier<String> defaultCorrelationIdSupplier) {
        return getCorrelationId().orElseGet(defaultCorrelationIdSupplier);
    }

    /**
     * Removes the {@code correlationId} from the default slf4j {@link org.slf4j.MDC}.
     */
    public static void removeCorrelationId() {
        MDC.remove(X_CORRELATION_ID);
    }

    /**
     * Removes the correlation ID from the default slf4j {@link org.slf4j.MDC}.
     */
    private static void removeCorrelationId(final DiagnosticLoggingAdapter loggingAdapter) {
        removeCustomField(loggingAdapter, X_CORRELATION_ID);
    }

    /**
     * Removes the {@code fieldName} from the default slf4j {@link org.slf4j.MDC}.
     */
    public static void removeCustomField(final DiagnosticLoggingAdapter loggingAdapter, final String fieldName) {
        final Map<String, Object> mdc = getMDC(loggingAdapter);
        mdc.remove(fieldName);
        loggingAdapter.setMDC(mdc);
    }

    /**
     * Creates a new MDC field.
     * @param name the name of the field.
     * @param value the value of the field.
     * @return the MDC field for name and value.
     * @throws java.lang.NullPointerException if {@code name} is null.
     */
    public static MdcField newMdcField(final String name, @Nullable final String value) {
        return new ImmutableMdcField(name, value);
    }

    /**
     * @return a copy of the MDC of {@code loggingAdapter}.
     */
    private static Map<String, Object> getMDC(final DiagnosticLoggingAdapter loggingAdapter) {
        return new HashMap<>(loggingAdapter.getMDC());
    }

    /**
     * Represents an MDC field that can be added to the MDC of a logger.
     */
    public interface MdcField {

        /**
         * Get the name of the MDC field.
         * @return the name.
         */
        String getName();

        /**
         * Get the value of the MDC field.
         * @return the value.
         */
        @Nullable
        String getValue();
    }

    /**
     * Immutable implementation of {@code MdcField}.
     */
    @Immutable
    private static final class ImmutableMdcField implements MdcField  {

        private final String name;
        @Nullable private final String value;

        private ImmutableMdcField(final String name, @Nullable final String value) {
            this.name = checkNotNull(name);
            this.value = value;
        }


        @Override
        public String getName() {
            return name;
        }

        @Override
        @Nullable
        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ImmutableMdcField that = (ImmutableMdcField) o;
            return Objects.equals(name, that.name) &&
                    Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    ", name=" + name +
                    ", value=" + value +
                    "]";
        }

    }

}
