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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.javadsl.Sink;

/**
 * An Akka stream sink from a consumer of messages with sender.
 */
public final class Consume {

    private Consume() {
        throw new AssertionError();
    }

    /**
     * Create sink from consumer of {@code WithSender}.
     *
     * @param consumer the consumer.
     * @param <T> type of messages.
     * @param <S> some supertype of messages.
     * @return sink.
     */
    @SuppressWarnings("unchecked")
    public static <S extends WithDittoHeaders, T extends S> Graph<SinkShape<WithSender<T>>, NotUsed> of(final Consumer<WithSender<S>> consumer) {
        // need to cast WithSender<T> to WithSender<S> because java does not understand covariance
        return Sink.<WithSender<T>>foreach(w -> consumer.accept((WithSender<S>) w))
                .mapMaterializedValue(x -> NotUsed.getInstance());
    }

    /**
     * Create sink from bi-consumer of message and sender.
     *
     * @param biConsumer the bi-consumer.
     * @param <T> type of messages.
     * @return sink.
     */
    public static <T extends WithDittoHeaders> Graph<SinkShape<WithSender<T>>, NotUsed> of(final BiConsumer<? super T, ActorRef> biConsumer) {
        return of(withSender -> biConsumer.accept(withSender.getMessage(), withSender.getSender()));
    }

    /**
     * Create sink from consumer accepting all message types.
     *
     * @param consumer the consumer.
     * @return sink.
     */
    @SuppressWarnings("unchecked")
    public static Graph<SinkShape<WithSender<?>>, NotUsed> untyped(final Consumer<WithSender<?>> consumer) {

        // Ignore complaints from Java type checker. The consumer can clearly handle all inputs.
        return (Graph<SinkShape<WithSender<?>>, NotUsed>) (Object) of(consumer::accept);
    }
}
