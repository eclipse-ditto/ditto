/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import akka.actor.ActorRef;
import akka.event.LoggingAdapter;
import akka.stream.Attributes;
import akka.stream.Inlet;
import akka.stream.SinkShape;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

/**
 * An Akka stream sink from a consumer of messages with sender.
 */
public class Consume<T> extends GraphStage<SinkShape<WithSender<T>>> {

    private final SinkShape<WithSender<T>> shape = SinkShape.of(Inlet.create("input"));
    private final BiConsumer<WithSender<T>, LoggingAdapter> consumer;

    private Consume(final BiConsumer<WithSender<T>, LoggingAdapter> consumer) {
        this.consumer = consumer;
    }

    /**
     * Create sink from consumer of {@code WithSender}.
     *
     * @param consumer the consumer.
     * @param <T> type of messages.
     * @return sink.
     */
    public static <T> Consume<T> of(final Consumer<WithSender<? super T>> consumer) {
        return new Consume<>((withSender, log) -> consumer.accept(withSender));
    }

    /**
     * Create sink from biconsumer of message and sender.
     *
     * @param consumer the consumer.
     * @param <T> type of messages.
     * @return sink.
     */
    public static <T> Consume<T> of(final BiConsumer<? super T, ActorRef> consumer) {
        return new Consume<>((withSender, log) -> consumer.accept(withSender.message(), withSender.sender()));
    }

    /**
     * Create sink from biconsumer of {@code WithHeader} and {@code LoggingAdapter}.
     *
     * @param biConsumer the consumer.
     * @param <T> type of message.
     * @return sink.
     */
    public static <T> Consume<T> withLogger(final BiConsumer<WithSender<? super T>, LoggingAdapter> biConsumer) {
        return new Consume<>(biConsumer::accept);
    }

    /**
     * Create sink from consumer accepting all message types.
     *
     * @param biConsumer the consumer.
     * @return sink.
     */
    @SuppressWarnings("unchecked")
    public static GraphStage<SinkShape<WithSender>> untypedWithLogger(
            final BiConsumer<WithSender, LoggingAdapter> biConsumer) {

        // Ignore complaints from Java type checker. The biConsumer can clearly handle all inputs.
        return (GraphStage<SinkShape<WithSender>>) (Object) new Consume<>(biConsumer::accept);
    }

    @Override
    public SinkShape<WithSender<T>> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(final Attributes inheritedAttributes) throws Exception {
        return new ControlFlowLogic(shape) {
            {
                initOutlets(shape);
                when(shape.in(), withSender -> {
                    consumer.accept(withSender, log());
                });
            }
        };
    }
}
