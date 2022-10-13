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

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.KamonHttpContextPropagation;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.PreparedTrace;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.StartedTrace;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.Traces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for tracing within Ditto.
 * By default, tracing is disabled.
 * Before tracing can be used, {@link DittoTracing#init(TracingConfig)} has to be called.
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
     * Creates a new trace for an operation with the given name.
     *
     * @param headers the headers to derive the trace context from.
     * @param operationName the name of the operation.
     * @return the new prepared trace.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalStateException if {@link #init(TracingConfig)} was not called beforehand.
     */
    public static PreparedTrace newPreparedTrace(
            final Map<String, String> headers,
            final TraceOperationName operationName
    ) {
        final var state = getState();
        return state.newPreparedTrace(headers, operationName);
    }

    /**
     * Creates and starts a trace for the specified arguments.
     *
     * @param headers the headers to derive the trace context from.
     * @param startedTimer provides the name, the start and the finish time of the returned trace.
     * @return the new started trace which will be automatically stopped by {@code startedTimer}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalStateException if {@link #init(TracingConfig)} was not called beforehand.
     */
    public static StartedTrace newStartedTraceByTimer(
            final Map<String, String> headers,
            final StartedTimer startedTimer
    ) {
        final var state = getState();
        return state.newStartedTraceByTimer(headers, startedTimer);
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

        void init(TracingConfig tracingConfig);

        PreparedTrace newPreparedTrace(Map<String, String> headers, TraceOperationName operationName);

        StartedTrace newStartedTraceByTimer(Map<String, String> headers, StartedTimer startedTimer);

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
                        new TracingEnabledState(tryToGetKamonHttpContextPropagation(propagationChannelName))
                );
                LOGGER.info("Ditto tracing initialized and enabled using propagation channel <{}>.",
                        propagationChannelName);
            } else {
                newStateConsumer.accept(new TracingDisabledState());
                LOGGER.info("Ditto tracing is disabled. No traces are generated and trace context is not propagated.");
            }
        }

        private static KamonHttpContextPropagation tryToGetKamonHttpContextPropagation(
                final CharSequence propagationChannelName
        ) {
            try {
                return KamonHttpContextPropagation.newInstanceForChannelName(propagationChannelName);
            } catch (final IllegalArgumentException e) {
                throw new DittoConfigError(e.getMessage(), e);
            }
        }

        @Override
        public PreparedTrace newPreparedTrace(
                final Map<String, String> headers,
                final TraceOperationName operationName
        ) {
            throw newIllegalStateException();
        }

        private static IllegalStateException newIllegalStateException() {
            return new IllegalStateException("Operation not allowed in uninitialized state.");
        }

        @Override
        public StartedTrace newStartedTraceByTimer(final Map<String, String> headers, final StartedTimer startedTimer) {
            throw newIllegalStateException();
        }

    }

    private static final class TracingEnabledState implements DittoTracingState {

        private final KamonHttpContextPropagation kamonHttpContextPropagation;

        private TracingEnabledState(final KamonHttpContextPropagation kamonHttpContextPropagation) {
            this.kamonHttpContextPropagation = checkNotNull(kamonHttpContextPropagation, "kamonHttpContextPropagation");
        }

        @Override
        public void init(final TracingConfig tracingConfig) {
            // Nothing to do because already initialized.
        }

        @Override
        public PreparedTrace newPreparedTrace(
                final Map<String, String> headers,
                final TraceOperationName operationName
        ) {
            return Traces.newPreparedKamonTrace(headers, operationName, kamonHttpContextPropagation);
        }

        @Override
        public StartedTrace newStartedTraceByTimer(final Map<String, String> headers, final StartedTimer startedTimer) {
            checkNotNull(startedTimer, "startedTimer");
            final var preparedKamonTrace = Traces.newPreparedKamonTrace(
                    headers,
                    TraceOperationName.of(startedTimer.getName()),
                    kamonHttpContextPropagation
            );
            return preparedKamonTrace.startBy(startedTimer);
        }

    }

    @Immutable
    private static final class TracingDisabledState implements DittoTracingState {

        @Override
        public void init(final TracingConfig tracingConfig) {
            // Nothing to do because already initialized.
        }

        @Override
        public PreparedTrace newPreparedTrace(
                final Map<String, String> headers,
                final TraceOperationName operationName
        ) {
            return Traces.emptyPreparedTrace(operationName);
        }

        @Override
        public StartedTrace newStartedTraceByTimer(final Map<String, String> headers, final StartedTimer startedTimer) {
            checkNotNull(startedTimer, "startedTimer");
            return Traces.emptyStartedTrace(TraceOperationName.of(startedTimer.getName()));
        }

    }

}
