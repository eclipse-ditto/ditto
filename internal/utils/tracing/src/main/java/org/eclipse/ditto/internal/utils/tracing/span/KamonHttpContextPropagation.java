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
package org.eclipse.ditto.internal.utils.tracing.span;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.utils.result.Result;

import kamon.Kamon;
import kamon.context.Context;
import kamon.context.HttpPropagation;
import kamon.context.Propagation;
import scala.Option;
import scala.jdk.javaapi.CollectionConverters;

/**
 * This class provides the means to read {@link Context} from a Map of headers as well as propagating {@code Context}
 * to a Map of headers.
 */
@NotThreadSafe
public final class KamonHttpContextPropagation {

    private final Propagation<HttpPropagation.HeaderReader, HttpPropagation.HeaderWriter> propagation;

    private KamonHttpContextPropagation(
            final Propagation<HttpPropagation.HeaderReader, HttpPropagation.HeaderWriter> propagation
    ) {
        this.propagation = propagation;
    }

    /**
     * Creates an instance of {@code KamonHttpContextPropagation} for the specified propagation channel name
     * argument.
     *
     * @param propagationChannelName configured name of the HTTP propagation channel.
     * @return {@code Ok} containing the new instance, if successful, otherwise an {@code Err} containing an
     * {@code IllegalArgumentException} if {@code propagationChannelName} is undefined.
     * @throws NullPointerException if {@code propagationChannelName} is {@code null}.
     */
    public static Result<KamonHttpContextPropagation, Throwable> newInstanceForChannelName(
            final CharSequence propagationChannelName
    ) {
        checkNotNull(propagationChannelName, "propagationChannelName");
        return Kamon.httpPropagation(propagationChannelName.toString())
                .map(KamonHttpContextPropagation::new)
                .map(Result::ok)
                .getOrElse(() -> Result.err(new IllegalArgumentException(
                    MessageFormat.format(
                            "HTTP propagation for channel name <{0}> is undefined.",
                            propagationChannelName
                    )
            )));
    }

    /**
     * Returns an instance of {@code KamonHttpContextPropagation} for the default HTTP propagation channel.
     *
     * @return the instance.
     */
    static KamonHttpContextPropagation getInstanceForDefaultHttpChannel() {
        return new KamonHttpContextPropagation(Kamon.defaultHttpPropagation());
    }

    /**
     * Reads context information from the specified map of headers.
     *
     * @param headers the map of headers to read the context from.
     * @return the read context.
     * @throws NullPointerException if {@code headers} is {@code null}.
     */
    public Context getContextFromHeaders(final Map<String, String> headers) {
        return propagation.read(new MapHeaderReader(checkNotNull(headers, "headers")));
    }

    /**
     * Propagates the specified context trace context to the specified map.
     *
     * @param context the context to be propagated to the specified map.
     * @param headers the map to which the specified context is propagated.
     * @return a modifiable Map consisting of the content of {@code headers} and the propagated context information.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public Map<String, String> propagateContextToHeaders(final Context context, final Map<String, String> headers) {
        checkNotNull(context, "context");
        final var result = getMutableCopyOfMap(checkNotNull(headers, "headers"));
        propagation.write(context, result::put);
        return result;
    }

    private static <K, V> Map<K, V> getMutableCopyOfMap(final Map<K, V> map) {
        return new HashMap<>(map);
    }

    private static final class MapHeaderReader implements HttpPropagation.HeaderReader {

        private final Map<String, String> map;

        private MapHeaderReader(final Map<String, String> map) {
            this.map = map;
        }

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
