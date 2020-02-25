/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.ArrayDeque;
import java.util.Queue;

import org.eclipse.ditto.model.base.exceptions.TooManyRequestsException;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;

import akka.actor.ActorRef;
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.Shape;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

/**
 * A {@link GraphStage} that buffers incoming elements up to the given {@link #bufferMaxSize}.
 * If the buffer is full this stage responds immediately with a {@link TooManyRequestsException} in case the incoming
 * element is of type {@link WithSender}.
 *
 * @param <T> The type of elements this buffer should process.
 * @since 1.1.0
 */
final class ErrorRespondingBuffer<T> extends GraphStage<FlowShape<T, T>> {

    private final Inlet<T> in = Inlet.create("ErrorRespondingBuffer.in");
    private final Outlet<T> out = Outlet.create("ErrorRespondingBuffer.out");

    private final FlowShape<T, T> shape = FlowShape.of(in, out);
    private final int bufferMaxSize;
    private final Gauge gauge;

    private ErrorRespondingBuffer(final int bufferSize,
            final Gauge gauge) {
        bufferMaxSize = bufferSize;
        this.gauge = gauge;
    }

    /**
     * Creates a new instance of this buffering stage with the given buffer size reporting buffer usage to the
     * given gauge.
     *
     * @param bufferSize The maximum number of elements this component should buffer.
     * @param gauge Gauge of queue sizes.
     * @param <T> The type of elements this component should buffer.
     * @return the new instance.
     */
    static <T> ErrorRespondingBuffer<T> of(final int bufferSize, final Gauge gauge) {
        return new ErrorRespondingBuffer<>(bufferSize, gauge);
    }

    @Override
    public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
        return new BufferLogic(shape);
    }

    @Override
    public FlowShape<T, T> shape() {
        return shape;
    }

    private class BufferLogic extends GraphStageLogic {

        private final Queue<T> buffer;
        private boolean downstreamWaiting;

        public BufferLogic(final Shape shape) {
            super(shape);

            buffer = new ArrayDeque<>(bufferMaxSize);
            downstreamWaiting = false;

            setHandler(in, new AbstractInHandler() {
                @Override
                public void onPush() {
                    final T elem = grab(in);

                    if (isBufferFull() && elem instanceof WithSender) {
                        final WithSender<?> withSender = (WithSender<?>) elem;
                        final TooManyRequestsException exception = TooManyRequestsException.newBuilder()
                                .dittoHeaders(withSender.getMessage().getDittoHeaders())
                                .build();
                        withSender.getSender().tell(exception, ActorRef.noSender());
                    }

                    if (downstreamWaiting) {
                        downstreamWaiting = false;
                        push(out, elem);
                    } else {
                        buffer.add(elem);
                    }

                    pull(in);
                    gauge.set((long) buffer.size());
                }

                @Override
                public void onUpstreamFinish() {
                    if (!buffer.isEmpty()) {
                        // emit the rest if possible
                        emitMultiple(out, buffer.iterator());
                    }
                    completeStage();
                }
            });

            setHandler(out, new AbstractOutHandler() {
                @Override
                public void onPull() {
                    if (buffer.isEmpty()) {
                        downstreamWaiting = true;
                    } else {
                        final T elem = buffer.poll();
                        push(out, elem);
                    }
                    if (!hasBeenPulled(in)) {
                        pull(in);
                    }
                }
            });
        }

        private boolean isBufferFull() {
            return buffer.size() == bufferMaxSize;
        }

    }

}
