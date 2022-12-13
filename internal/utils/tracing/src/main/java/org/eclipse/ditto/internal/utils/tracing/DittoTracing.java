/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;
import org.eclipse.ditto.internal.utils.tracing.filter.TracingFilter;
import org.eclipse.ditto.internal.utils.tracing.span.KamonHttpContextPropagation;
import org.eclipse.ditto.internal.utils.tracing.span.PreparedSpan;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;
import org.eclipse.ditto.internal.utils.tracing.span.TracingSpans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for tracing within Ditto.
 * Before tracing spans can be created, {@link DittoTracing#init(TracingConfig)} has to be called.
 */
@ThreadSafe
public final class DittoTracing {

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoTracing.class);

    private final AtomicReference<DittoTracingState> stateHolder;

    private DittoTracing() {
        super();
        stateHolder = new AtomicReference<>();
        stateHolder.set(new UninitializedState(stateHolder::set));
    }

    /**
     * Initializes DittoTracing with the specified {@link TracingConfig} argument.
     *
     * @param tracingConfig the TracingConfig
     * @throws NullPointerException if {@code tracingConfig} is {@code null}.
     * @throws DittoConfigError if {@code tracingConfig} provides an invalid value for Kamon propagation channel name.
     */
    public static void init(final TracingConfig tracingConfig) {
        final var state = getState();
        state.init(tracingConfig);
    }

    private static DittoTracingState getState() {
        final var tracingInstance = getInstance();
        return tracingInstance.stateHolder.get();
    }

    private static DittoTracing getInstance() {

        // Initialize-on-demand holder class idiom.
        return InstanceHolder.INSTANCE;
    }

    /**
     * Creates a new {@code PreparedSpan} for an operation with the given name.
     *
     * @param headers the headers to derive the span context from.
     * @param operationName the name of the operation.
     * @return the new prepared span.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalStateException if {@link #init(TracingConfig)} was not called beforehand.
     */
    public static PreparedSpan newPreparedSpan(
            final Map<String, String> headers,
            final SpanOperationName operationName
    ) {
        final var state = getState();
        return state.newPreparedSpan(headers, operationName);
    }

    /**
     * Creates and starts a span for the specified arguments.
     *
     * @param headers the headers to derive the span context from.
     * @param startedTimer provides the name, the start and the finish time of the returned span.
     * @return the new started span which will be automatically stopped by {@code startedTimer}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalStateException if {@link #init(TracingConfig)} was not called beforehand.
     */
    public static StartedSpan newStartedSpanByTimer(
            final Map<String, String> headers,
            final StartedTimer startedTimer
    ) {
        final var state = getState();
        return state.newStartedSpanByTimer(headers, startedTimer);
    }

    /**
     * Resets DittoTracing to uninitialized state.
     * This is the inverse function of {@link DittoTracing#init(TracingConfig)}.
     */
    static void reset() {
        final var instance = getInstance();
        instance.stateHolder.set(new UninitializedState(instance.stateHolder::set));
    }

    private static final class InstanceHolder {

        private static final DittoTracing INSTANCE = new DittoTracing();

    }

    private static interface DittoTracingState {

        default void init(final TracingConfig tracingConfig) {
            throw new IllegalStateException(MessageFormat.format(
                    "{0} was already initialized. Please ensure that initialization is only performed once.",
                    DittoTracing.class.getSimpleName()
            ));
        }

        PreparedSpan newPreparedSpan(Map<String, String> headers, SpanOperationName operationName);

        StartedSpan newStartedSpanByTimer(Map<String, String> headers, StartedTimer startedTimer);

    }

    private static final class UninitializedState implements DittoTracingState {

        private final Consumer<DittoTracingState> newStateConsumer;

        private UninitializedState(final Consumer<DittoTracingState> newStateConsumer) {
            this.newStateConsumer = checkNotNull(newStateConsumer, "newStateConsumer");
        }

        @Override
        public void init(final TracingConfig tracingConfig) {
            checkNotNull(tracingConfig, "tracingConfig");
            if (tracingConfig.isTracingEnabled()) {
                final var propagationChannelName = tracingConfig.getPropagationChannel();
                newStateConsumer.accept(
                        new TracingEnabledState(
                                getKamonHttpContextPropagationOrThrow(propagationChannelName),
                                tracingConfig.getTracingFilter()
                        )
                );
                LOGGER.info("Ditto tracing initialized and enabled using propagation channel <{}>.",
                        propagationChannelName);
            } else {
                newStateConsumer.accept(new TracingDisabledState());
                LOGGER.info("Ditto tracing is disabled. No traces are generated and span context is not propagated.");
            }
        }

        private static KamonHttpContextPropagation getKamonHttpContextPropagationOrThrow(
                final CharSequence propagationChannelName
        ) {
            return KamonHttpContextPropagation.newInstanceForChannelName(propagationChannelName)
                    .mapErr(throwable -> new DittoConfigError(throwable.getMessage(), throwable))
                    .orElseThrow();
        }

        @Override
        public PreparedSpan newPreparedSpan(final Map<String, String> headers, final SpanOperationName operationName) {
            throw newIllegalStateException();
        }

        private static IllegalStateException newIllegalStateException() {
            return new IllegalStateException("Operation not allowed in uninitialized state.");
        }

        @Override
        public StartedSpan newStartedSpanByTimer(final Map<String, String> headers, final StartedTimer startedTimer) {
            throw newIllegalStateException();
        }

    }

    private static final class TracingEnabledState implements DittoTracingState {

        private final KamonHttpContextPropagation kamonHttpContextPropagation;
        private final TracingFilter tracingFilter;

        private TracingEnabledState(
                final KamonHttpContextPropagation kamonHttpContextPropagation,
                final TracingFilter tracingFilter
        ) {
            this.kamonHttpContextPropagation = checkNotNull(kamonHttpContextPropagation, "kamonHttpContextPropagation");
            this.tracingFilter = checkNotNull(tracingFilter, "tracingFilter");
        }

        @Override
        public PreparedSpan newPreparedSpan(final Map<String, String> headers, final SpanOperationName operationName) {
            final PreparedSpan result;
            if (tracingFilter.accept(operationName)) {
                result = TracingSpans.newPreparedKamonSpan(headers, operationName, kamonHttpContextPropagation);
            } else {
                result = TracingSpans.emptyPreparedSpan(operationName);
            }
            return result;
        }

        @Override
        public StartedSpan newStartedSpanByTimer(final Map<String, String> headers, final StartedTimer startedTimer) {
            checkNotNull(startedTimer, "startedTimer");
            final StartedSpan result;
            final var operationName = SpanOperationName.of(startedTimer.getName());
            if (tracingFilter.accept(operationName)) {
                final var preparedKamonSpan = TracingSpans.newPreparedKamonSpan(
                        headers,
                        operationName,
                        kamonHttpContextPropagation
                );
                result = preparedKamonSpan.startBy(startedTimer);
            } else {
                result = TracingSpans.emptyStartedSpan(operationName);
            }
            return result;
        }

    }

    @Immutable
    private static final class TracingDisabledState implements DittoTracingState {

        @Override
        public PreparedSpan newPreparedSpan(final Map<String, String> headers, final SpanOperationName operationName) {
            return TracingSpans.emptyPreparedSpan(operationName);
        }

        @Override
        public StartedSpan newStartedSpanByTimer(final Map<String, String> headers, final StartedTimer startedTimer) {
            checkNotNull(startedTimer, "startedTimer");
            return TracingSpans.emptyStartedSpan(SpanOperationName.of(startedTimer.getName()));
        }

    }

}
