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
package org.eclipse.ditto.internal.utils.tracing;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.PreparedTrace;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.Traces;

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
 * contexts.
 */
public final class DittoTracing {

    private static final Propagation<HttpPropagation.HeaderReader, HttpPropagation.HeaderWriter>
            PROPAGATION = Kamon.httpPropagation("ditto").get();

    private DittoTracing() {}

    /**
     * Creates a new trace for an operation with the given name.
     *
     * @param context the trace context
     * @param name the name of the operation.
     * @return the new prepared trace.
     */
    public static PreparedTrace trace(final Context context, final String name) {
        return Traces.newTrace(context, name);
    }

    /**
     * Creates a new trace for an operation with the given name.
     *
     * @param withDittoHeaders a WithDittoHeaders instance containing trace information
     * @param name the name of the operation.
     * @return the new prepared trace.
     */
    public static PreparedTrace trace(final WithDittoHeaders withDittoHeaders, final String name) {
        final Context extractedTraceContext = extractTraceContext(withDittoHeaders);
        final PreparedTrace preparedTrace = Traces.newTrace(extractedTraceContext, name);
        withDittoHeaders.getDittoHeaders().getCorrelationId().ifPresent(preparedTrace::correlationId);
        return preparedTrace;
    }

    /**
     * Creates a new trace for an operation with the given name.
     *
     * @param dittoHeaders a DittoHeaders instance containing trace information
     * @param name the name of the operation.
     * @return the new prepared trace.
     */
    public static PreparedTrace trace(final DittoHeaders dittoHeaders, final String name) {
        final Context extractedTraceContext = extractTraceContext(dittoHeaders);
        final PreparedTrace preparedTrace = Traces.newTrace(extractedTraceContext, name);
        dittoHeaders.getCorrelationId().ifPresent(preparedTrace::correlationId);
        return preparedTrace;
    }

    /**
     * Extracts a trace context from an HttpRequest.
     *
     * @param request the request from which to extract tracing information
     * @return the extracted trace context
     */
    public static Context extractTraceContext(final HttpRequest request) {
        final Map<String, String> map = new HashMap<>();
        Optional.of(request)
                .map(HttpMessage::getHeaders)
                .ifPresent(headers -> headers.forEach(httpHeader -> map.put(httpHeader.name(), httpHeader.value())));
        return extractTraceContext(map);
    }

    /**
     * Extracts a trace context from a RequestContext.
     *
     * @param requestContext the request context from which to extract tracing information
     * @return the extracted trace context
     */
    public static Context extractTraceContext(final RequestContext requestContext) {
        final Map<String, String> map = new HashMap<>();
        Optional.of(requestContext)
                .map(RequestContext::getRequest)
                .map(HttpMessage::getHeaders)
                .ifPresent(headers -> headers.forEach(httpHeader -> map.put(httpHeader.name(), httpHeader.value())));
        return extractTraceContext(map);
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
        return PROPAGATION.read(new MapHeaderReader(map));
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
        if (context.isEmpty()) {
            return command;
        } else {
            final DittoHeadersBuilder<?, ?> dittoHeaders = command.getDittoHeaders().toBuilder();
            PROPAGATION.write(context, dittoHeaders::putHeader);
            return (T) command.setDittoHeaders(dittoHeaders.build());
        }
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
        if (context.isEmpty()) {
            return signal;
        } else {
            final DittoHeadersBuilder<?, ?> dittoHeaders = signal.getDittoHeaders().toBuilder();
            PROPAGATION.write(context, dittoHeaders::putHeader);
            return (T) signal.setDittoHeaders(dittoHeaders.build());
        }
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
        if (context.isEmpty()) {
            return dittoHeadersSettable;
        } else {
            final DittoHeadersBuilder<?, ?> dittoHeaders = dittoHeadersSettable.getDittoHeaders().toBuilder();
            PROPAGATION.write(context, dittoHeaders::putHeader);
            return (T) dittoHeadersSettable.setDittoHeaders(dittoHeaders.build());
        }
    }

    /**
     * Propagate trace context to DittoHeaders.
     *
     * @param context the trace context to propagate
     * @param dittoHeaders the dittoHeaders to which the trace context is attached
     * @return the dittoHeaders with the trace context written to the ditto headers
     */
    public static DittoHeaders propagateContext(final Context context, final DittoHeaders dittoHeaders) {
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = dittoHeaders.toBuilder();
        PROPAGATION.write(context, dittoHeadersBuilder::putHeader);
        return dittoHeadersBuilder.build();
    }

    /**
     * Propagate trace context to T using the given setter. This can be used for immutable types which return a new
     * instance when e.g. adding a new header.
     *
     * @param context the trace context to propagate
     * @param identity the identity of T
     * @param setter a setter that adds a new header to an instance of T
     * @param <T> the type holding the trace information
     * @return the instance of T with the trace context added
     */
    public static <T> T propagateContext(final Context context,
            final T identity, final BiFunction<T, Map.Entry<String, String>, T> setter) {
        final AtomicReference<T> carrier = new AtomicReference<>(identity);
        PROPAGATION.write(context,
                (key, value) -> carrier.set(setter.apply(carrier.get(), Map.entry(key, value))));
        return carrier.get();
    }

    /**
     * Propagate trace context to a new map.
     *
     * @param context the trace context to propagate
     * @return the map containing the trace context
     */
    public static Map<String, String> propagateContext(final Context context) {
        final Map<String, String> map = new HashMap<>();
        PROPAGATION.write(context, map::put);
        return map;
    }

    /**
     * Propagate trace context to a given map.
     *
     * @param context the trace context to propagate
     * @return the map containing the trace context
     */
    public static Map<String, String> propagateContext(final Map<String, String> headers, final Context context) {
        final Map<String, String> map = new HashMap<>(headers);
        PROPAGATION.write(context, map::put);
        return Map.copyOf(map);
    }

    /**
     * Implementation of {@link kamon.context.HttpPropagation.HeaderReader}.
     */
    private static class MapHeaderReader implements HttpPropagation.HeaderReader {

        private final Map<String, String> map;

        /**
         * Instantiates a new MapHeaderReader.
         *
         * @param map the map from which to read the trace information.
         */
        public MapHeaderReader(final Map<String, String> map) {this.map = map;}

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
