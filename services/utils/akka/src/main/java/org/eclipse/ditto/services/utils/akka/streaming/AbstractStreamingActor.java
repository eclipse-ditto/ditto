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
package org.eclipse.ditto.services.utils.akka.streaming;

import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_COMPLETED;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_FAILED;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_STARTED;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Abstract actor that responds to each command by streaming elements from a source to the sender of the command.
 *
 * @param <C> Type of commands to start a stream.
 * @param <E> Type of elements of a stream.
 */
public abstract class AbstractStreamingActor<C, E> extends AbstractActor {

    /**
     * Logger for this actor.
     */
    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    /**
     * Actor materializer of this actor's system.
     */
    protected final ActorMaterializer materializer = ActorMaterializer.create(getContext());

    /**
     * @return Class of the commands.
     */
    protected abstract Class<C> getCommandClass();

    /**
     * Extract batch size from a command. The rate specifies the number of elements to be sent per message.
     * Default to 1.
     *
     * @param command The command to start a stream.
     * @return The number of elements to be streamed per second.
     */
    protected Optional<Integer> getBurst(final C command) {
        return Optional.of(1);
    }

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

    /**
     * Extract timeout in milliseconds.
     *
     * @param command The command to start a stream.
     * @return Timeout in milliseconds.
     */
    protected abstract Optional<Long> getTimeoutMillis(final C command);

    /**
     * Starts a source of elements according to the command.
     *
     * @param command The command to start a stream.
     * @return A source of elements to stream to the recipient.
     */
    protected abstract Source<E, NotUsed> createSource(final C command);

    @Override
    public final Receive createReceive() {
        return ReceiveBuilder.create()
                .match(getCommandClass(), this::startStreaming)
                .matchAny(message -> log.warning("Unexpected message: <{}>", message))
                .build();
    }

    private void startStreaming(final C command) {
        log.debug("Starting streaming due to command: {}", command);
        final ActorRef recipient = getSender();
        final int burst = getBurst(command).orElse(1);
        final long timeoutMillis = getTimeoutMillis(command).orElse(60_000L);

        // The stream below has the behavior of Sink.actorRefWithAck:
        // - STREAM_STARTED is sent as first message expecting acknowledgement,
        // - each batched stream message is sent expecting acknowledgement,
        // - STREAM_COMPLETED is sent when upstream completes successfully,
        // - STREAM_FAILURE is sent when upstream fails.
        //
        // DO NOT replace the stream below by Sink.actorRefWithAck from Akka 2.5.8,
        // because it sends STREAM_COMPLETED without waiting for the acknowledgement
        // of the final stream element, complicating the state transition of
        // AbstractStreamForwarder.
        //
        // See:
        // https://github.com/akka/akka/issues/21015
        createSource(command)
                .grouped(burst)
                .map(this::batchMessages)
                .prepend(Source.single(STREAM_STARTED))
                .concat(Source.single(STREAM_COMPLETED))
                .mapAsync(1, message -> {
                    if (STREAM_COMPLETED.equals(message)) {
                        recipient.tell(message, ActorRef.noSender());
                        return CompletableFuture.completedFuture(message);
                    } else {
                        return PatternsCS.ask(recipient, message, timeoutMillis);
                    }
                })
                .recoverWithRetries(1,
                        new PFBuilder<Throwable, Source<Object, NotUsed>>()
                                .matchAny(error -> {
                                    recipient.tell(STREAM_FAILED, ActorRef.noSender());
                                    return Source.single(STREAM_FAILED);
                                })
                                .build())
                .log("future completed", log)
                .runWith(Sink.ignore(), materializer);
    }
}
