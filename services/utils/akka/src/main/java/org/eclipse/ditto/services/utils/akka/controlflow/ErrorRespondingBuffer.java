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

public final class ErrorRespondingBuffer<T> extends GraphStage<FlowShape<T, T>> {

    public final Inlet<T> in = Inlet.create("ErrorRespondingBuffer.in");
    public final Outlet<T> out = Outlet.create("ErrorRespondingBuffer.out");

    private final FlowShape<T, T> shape = FlowShape.of(in, out);
    private final int bufferMaxSize;

    public ErrorRespondingBuffer(final int bufferSize) {
        bufferMaxSize = bufferSize;
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
