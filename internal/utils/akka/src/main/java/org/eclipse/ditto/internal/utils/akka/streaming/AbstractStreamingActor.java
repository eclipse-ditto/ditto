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
package org.eclipse.ditto.internal.utils.akka.streaming;

import java.time.Duration;
import java.util.List;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;

/**
 * Abstract actor that responds to each command by streaming elements from a source to the sender of the command.
 *
 * @param <C> Type of commands to start a stream.
 * @param <E> Type of elements of a stream.
 */
public abstract class AbstractStreamingActor<C, E> extends AbstractActor {

    /**
     * Logger for this actor.
     * Intentionally _NOT_ instantiated with DittoLoggerFactory as this would lead to scala-java compile problems within
     * the "ditto-internal-utils-akka" module:
     */
    protected final DiagnosticLoggingAdapter log = Logging.apply(this);

    /**
     * Actor materializer of this actor's system.
     */
    protected final Materializer materializer = Materializer.createMaterializer(this::getContext);

    /**
     * @return Class of the commands.
     */
    protected abstract Class<C> getCommandClass();

    /**
     * Extract batch size from a command. The rate specifies the number of elements to be sent per message.
     *
     * @param command The command to start a stream.
     * @return The number of elements to be streamed per second.
     */
    protected abstract int getBurst(final C command);

    /**
     * Extract initial timeout.
     *
     * @param command The command to start a stream.
     * @return The initial timeout.
     */
    protected abstract Duration getInitialTimeout(final C command);

    /**
     * Extract idle timeout.
     *
     * @param command The command to start a stream.
     * @return The idle timeout.
     */
    protected abstract Duration getIdleTimeout(final C command);

    /**
     * Starts a source of elements according to the command.
     *
     * @param command The command to start a stream.
     * @return A source of elements to stream to the recipient.
     */
    protected abstract Source<E, NotUsed> createSource(final C command);

    /**
     * Batch elements together into 1 message. Default to the first element of the list if it is a singleton and the
     * list itself otherwise.
     *
     * @param elements Elements from the source.
     * @return A batched message.
     */
    protected Object batchMessages(final List<E> elements) {
        return elements.size() == 1
                ? elements.get(0)
                : elements;
    }

    @Override
    public final Receive createReceive() {
        return ReceiveBuilder.create()
                .match(getCommandClass(), this::startStreaming)
                .matchAny(message -> log.warning("Unexpected message: <{}>", message))
                .build();
    }

    private void startStreaming(final C command) {
        log.debug("Starting streaming due to command: {}", command);
        final int burst = getBurst(command);
        final Duration initialTimeout = getInitialTimeout(command);
        final Duration idleTimeout = getIdleTimeout(command);

        final SourceRef<Object> sourceRef = createSource(command)
                .grouped(burst)
                .map(this::batchMessages)
                .initialTimeout(initialTimeout)
                .idleTimeout(idleTimeout)
                .runWith(StreamRefs.sourceRef(), materializer);

        getSender().tell(sourceRef, getSelf());
    }
}
