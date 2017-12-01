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

import static java.util.concurrent.TimeUnit.SECONDS;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.ThrottleMode;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.concurrent.duration.FiniteDuration;

/**
 * Abstract actor that responds to each command by streaming elements from a source to actors specified in the command.
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
     * Extract streaming rate from a command. The rate specifies the number of elements to be streamed per second.
     *
     * @param command The command to start a stream.
     * @return The number of elements to be streamed per second.
     */
    protected abstract int getRate(final C command);

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
        final ActorRef recipient = getSender();
        final int elementsPerSecond = getRate(command);
        final FiniteDuration second = FiniteDuration.create(1, SECONDS);

        createSource(command)
                .throttle(elementsPerSecond, second, elementsPerSecond, ThrottleMode.shaping())
                .runWith(Sink.actorRef(recipient, Done.getInstance()), materializer);

    }
}
