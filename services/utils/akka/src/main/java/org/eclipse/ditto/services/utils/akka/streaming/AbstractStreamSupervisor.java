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

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * An actor that supervises stream forwarders.
 */
public abstract class AbstractStreamSupervisor<C> extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final SupervisorStrategy supervisorStrategy =
            new OneForOneStrategy(true, DeciderBuilder.matchAny(e -> SupervisorStrategy.stop()).build());

    private Cancellable activityCheck;

    /**
     * Returns how often to try to start a stream.
     *
     * @return The poll interval.
     */
    protected abstract Duration getPollInterval();

    /**
     * Returns props to create a stream forwarder actor.
     *
     * @return The props.
     */
    protected abstract Props getStreamForwarderProps();

    /**
     * Computes the command to start a stream asynchronously.
     *
     * @return A future command to start a stream.
     */
    protected abstract CompletionStage<C> newStartStreamingCommand();

    /**
     * Returns the class of commands to start streams.
     *
     * @return The class of the command.
     */
    protected abstract Class<C> getCommandClass();

    /**
     * Returns the actor to request streams from.
     *
     * @return Reference to the streaming actor.
     */
    protected abstract ActorRef getStreamingActor();

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        final FiniteDuration delayAndInterval = FiniteDuration.create(getPollInterval().getSeconds(), TimeUnit.SECONDS);
        activityCheck = getContext().system().scheduler()
                .schedule(delayAndInterval, delayAndInterval, getSelf(), new CheckForActivity(),
                        getContext().dispatcher(), ActorRef.noSender());
    }

    @Override
    public void postStop() throws Exception {
        if (null != activityCheck) {
            activityCheck.cancel();
        }
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CheckForActivity.class, this::checkForActivity)
                .match(getCommandClass(), this::startChildActor)
                .build();
    }

    private void checkForActivity(final CheckForActivity message) {
        if (hasChild()) {
            getContext().getChildren().forEach(child -> log.info("Activity check - Stream is ongoing: {}", child));
        } else {
            newStartStreamingCommand().thenAccept(command -> getSelf().tell(command, ActorRef.noSender()));
        }
    }

    private void startChildActor(final C startStreamCommand) {
        if (hasChild()) {
            log.error("Got unexpected startStreamCommand while a stream is ongoing: <{}>", startStreamCommand);
        } else {
            final ActorRef streamingActor = getStreamingActor();
            final ActorRef child = getContext().actorOf(getStreamForwarderProps());
            log.info("Requesting stream from <{}> on behalf of <{}> by <{}>", streamingActor, child,
                    startStreamCommand);
            getStreamingActor().tell(startStreamCommand, child);
        }
    }

    private boolean hasChild() {
        return getContext().getChildren().iterator().hasNext();
    }

    private static final class CheckForActivity {}
}
