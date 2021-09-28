/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.PreparedTrace;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.StartedTrace;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.Traces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpMessage;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.server.RequestContext;
import kamon.Kamon;
import kamon.context.Context;
import kamon.context.HttpPropagation;
import kamon.context.Propagation;
import scala.Option;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Contains static method factories in order to build Ditto tracing instruments and to extract and propagate tracing a
 * contexts. Tracing is disabled by default and must be {@link #initialize}d to actually generate traces and
 * propagate tracing contexts.
 */
public final class DittoTracing {

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoTracing.class);

    private static Propagation<HttpPropagation.HeaderReader, HttpPropagation.HeaderWriter> propagation;
    private static boolean enabled = false;

    private DittoTracing() {}

    /**
     * Initialize DittoTracing with the given {@link org.eclipse.ditto.internal.utils.tracing.config.TracingConfig}.
     *
     * @param tracingConfig the TracingConfig
     */
    public static void initialize(final TracingConfig tracingConfig) {
        if (tracingConfig.isTracingEnabled()) {
            enabled = true;
            propagation = Kamon.httpPropagation(tracingConfig.getPropagationChannel()).get();
            LOGGER.info("Ditto tracing initialized and enabled using channel '{}'.",
                    tracingConfig.getPropagationChannel());
        } else {
            LOGGER.info("Ditto tracing is disabled. No traces are generated and trace context is not propagated.");
        }
    }

    /**
     * Provides a high precision {@code Instant.now()} using the Kamon {@code Clock} implementation suited for tracing.
     *
     * @return the current instant.
     */
    public static Instant getTracingInstantNow() {
        return Kamon.clock().instant();
    }

    /**
     * Creates a new trace for an operation with the given name.
     *
     * @param context the trace context
     * @param name the name of the operation
     * @return the new prepared trace
     */
    public static PreparedTrace trace(final Context context, final String name) {
        return doTrace(() -> Traces.newTrace(context, name));
    }

    /**
     * Creates a new trace for an operation with the given name.
     *
     * @param withDittoHeaders a WithDittoHeaders instance containing trace information
     * @param name the name of the operation
     * @return the new prepared trace
     */
    public static PreparedTrace trace(final WithDittoHeaders withDittoHeaders, final String name) {
        return doTrace(() -> {
            final Context extractedTraceContext = extractTraceContext(withDittoHeaders);
            final PreparedTrace preparedTrace = Traces.newTrace(extractedTraceContext, name);
            withDittoHeaders.getDittoHeaders().getCorrelationId().ifPresent(preparedTrace::correlationId);
            return preparedTrace;
        });
    }

    /**
     * Creates a new trace for an operation with the given name.
     *
     * @param dittoHeaders a DittoHeaders instance containing trace information
     * @param name the name of the operation
     * @return the new prepared trace
     */
    public static PreparedTrace trace(final DittoHeaders dittoHeaders, final String name) {
        return doTrace(() -> {
            final Context extractedTraceContext = extractTraceContext(dittoHeaders);
            final PreparedTrace preparedTrace = Traces.newTrace(extractedTraceContext, name);
            dittoHeaders.getCorrelationId().ifPresent(preparedTrace::correlationId);
            return preparedTrace;
        });
    }

    /**
     * Extracts a trace context from an Akka HTTP HttpRequest.
     *
     * @param request the request from which to extract tracing information
     * @return the extracted trace context
     */
    public static Context extractTraceContext(final HttpRequest request) {
        return doExtract(() -> {
            final Map<String, String> map = new HashMap<>();
            Optional.of(request)
                    .map(HttpMessage::getHeaders)
                    .ifPresent(
                            headers -> headers.forEach(httpHeader -> map.put(httpHeader.name(), httpHeader.value())));
            return extractTraceContext(map);
        });
    }

    /**
     * Extracts a trace context from an Akka HTTP RequestContext.
     *
     * @param requestContext the request context from which to extract tracing information
     * @return the extracted trace context
     */
    public static Context extractTraceContext(final RequestContext requestContext) {
        return doExtract(() -> {
            final Map<String, String> map = new HashMap<>();
            Optional.of(requestContext)
                    .map(RequestContext::getRequest)
                    .map(HttpMessage::getHeaders)
                    .ifPresent(
                            headers -> headers.forEach(httpHeader -> map.put(httpHeader.name(), httpHeader.value())));
            return extractTraceContext(map);
        });
    }


    /**
     * Extracts a trace context from a WithDittoHeaders.
     *
     * @param withDittoHeaders the WithDittoHeaders instance from which to extract tracing information
     * @return the extracted trace context
     */
    public static Context extractTraceContext(final WithDittoHeaders withDittoHeaders) {
        return extractTraceContext(withDittoHeaders.getDittoHeaders());
    }


    /**
     * Extracts a trace context from a map.
     *
     * @param map the map from which to extract tracing information
     * @return the extracted trace context
     */
    public static Context extractTraceContext(final Map<String, String> map) {
        return doExtract(() -> propagation.read(new MapHeaderReader(map)));
    }

    /**
     * Propagate trace context to a Command.
     *
     * @param context the trace context to propagate
     * @param command the command to which the trace context is attached
     * @param <T> the command type
     * @return the command with the trace context written to the ditto headers
     */
    public static <T extends Command<?>> T propagateContext(final Context context, final T command) {
        return doPropagate(context, command, () -> {
            final DittoHeadersBuilder<?, ?> dittoHeaders = command.getDittoHeaders().toBuilder();
            propagation.write(context, dittoHeaders::putHeader);
            //noinspection unchecked // need to cast because of ? in Command<?>
            return (T) command.setDittoHeaders(dittoHeaders.build());
        });
    }

    /**
     * Propagate trace context to a Signal.
     *
     * @param context the trace context to propagate
     * @param signal the signal to which the trace context is attached
     * @param <T> the signal type
     * @return the signal with the trace context written to the ditto headers
     */
    public static <T extends Signal<?>> T propagateContext(final Context context, final T signal) {
        return doPropagate(context, signal, () -> {
            final DittoHeadersBuilder<?, ?> dittoHeaders = signal.getDittoHeaders().toBuilder();
            propagation.write(context, dittoHeaders::putHeader);
            //noinspection unchecked // need to cast because of ? in Signal<?>
            return (T) signal.setDittoHeaders(dittoHeaders.build());
        });
    }

    /**
     * Propagate trace context to a DittoHeadersSettable.
     *
     * @param context the trace context to propagate
     * @param dittoHeadersSettable the dittoHeadersSettable to which the trace context is attached
     * @param <T> the DittoHeadersSettable type
     * @return the dittoHeadersSettable with the trace context written to the ditto headers
     */
    public static <T extends DittoHeadersSettable<?>> T propagateContext(final Context context,
            final T dittoHeadersSettable) {
        return doPropagate(context, dittoHeadersSettable, () -> {
            final DittoHeadersBuilder<?, ?> dittoHeaders = dittoHeadersSettable.getDittoHeaders().toBuilder();
            propagation.write(context, dittoHeaders::putHeader);
            //noinspection unchecked // need to cast because of ? in DittoHeadersSettable<?>
            return (T) dittoHeadersSettable.setDittoHeaders(dittoHeaders.build());
        });
    }

    /**
     * Propagate trace context to DittoHeaders.
     *
     * @param context the trace context to propagate
     * @param dittoHeaders the dittoHeaders to which the trace context is attached
     * @return the dittoHeaders with the trace context written to the ditto headers
     */
    public static DittoHeaders propagateContext(final Context context, final DittoHeaders dittoHeaders) {
        return doPropagate(context, dittoHeaders, () -> {
            final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = dittoHeaders.toBuilder();
            propagation.write(context, dittoHeadersBuilder::putHeader);
            return dittoHeadersBuilder.build();
        });
    }

    /**
     * Propagate trace context to the passed {@code identity} using the given setter.
     * This can be used for immutable types which return a new instance when e.g. adding a new header.
     *
     * @param context the trace context to propagate
     * @param identity the identity of type T
     * @param setter a setter that adds a new header to an instance of type T
     * @param <T> the type holding the trace information
     * @return the instance of type T with the trace context added
     */
    public static <T> T propagateContext(final Context context, final T identity,
            final BiFunction<T, Map.Entry<String, String>, T> setter) {
        return doPropagate(context, identity, () -> {
            final var ref = new Object() {
                T result = identity;
            };
            propagation.write(context,
                    (key, value) -> ref.result = setter.apply(ref.result, Map.entry(key, value)));
            return ref.result;
        });
    }

    /**
     * Propagate trace context to a new map.
     *
     * @param context the trace context to propagate
     * @return the map containing the trace context
     */
    public static Map<String, String> propagateContext(final Context context) {
        return doPropagate(context, Map.of(), () -> {
            final Map<String, String> map = new HashMap<>();
            propagation.write(context, map::put);
            return Map.copyOf(map);
        });
    }

    /**
     * Propagate trace context to a given map.
     *
     * @param context the trace context to propagate
     * @param headers the headers to propagate the trace context to
     * @return the map containing the trace context
     */
    public static Map<String, String> propagateContext(final Context context, final Map<String, String> headers) {
        return doPropagate(context, headers, () -> {
            final Map<String, String> map = new HashMap<>(headers);
            propagation.write(context, map::put);
            return Map.copyOf(map);
        });
    }

    /**
     * Wraps a StartedTimer and generates a trace span when the timer is finished.
     *
     * @param context the trace context
     * @param startedTimer the StartedTimer to wrap
     * @return the passed in trace context
     */
    public static Context wrapTimer(final Context context, final StartedTimer startedTimer) {
        final StartedTrace trace = trace(context, startedTimer.getName()).startAt(startedTimer.getStartInstant());
        startedTimer.onStop(stoppedTimer -> trace.tags(stoppedTimer.getTags())
                .finishAfter(stoppedTimer.getDuration()));
        return trace.getContext();
    }

    private static PreparedTrace doTrace(final Supplier<PreparedTrace> createTrace) {
        if (enabled) {
            return createTrace.get();
        } else {
            return Traces.emptyPreparedTrace();
        }
    }

    private static Context doExtract(final Supplier<Context> extract) {
        if (enabled) {
            return extract.get();
        } else {
            return Context.Empty();
        }
    }

    private static <T> T doPropagate(final Context context, final T t, final Supplier<T> propagate) {
        if (enabled && !context.isEmpty()) {
            return propagate.get();
        } else {
            return t;
        }
    }

    /**
     * Implementation of {@link kamon.context.HttpPropagation.HeaderReader}.
     */
    private static final class MapHeaderReader implements HttpPropagation.HeaderReader {

        private final Map<String, String> map;

        /**
         * Instantiates a new MapHeaderReader.
         *
         * @param map the map from which to read the trace information.
         */
        private MapHeaderReader(final Map<String, String> map) {this.map = map;}

        @Override
        public scala.collection.immutable.Map<String, String> readAll() {
            return scala.collection.immutable.Map.from(CollectionConverters.asScala(map));
        }

        @Override
        public Option<String> read(final String header) {
            return Option.apply(map.get(header));
        }
    }
}
