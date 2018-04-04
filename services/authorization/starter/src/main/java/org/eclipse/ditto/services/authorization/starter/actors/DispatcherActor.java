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
package org.eclipse.ditto.services.authorization.starter.actors;

import static akka.cluster.pubsub.DistributedPubSubMediator.Send;
import static akka.cluster.pubsub.DistributedPubSubMediator.Put;
import static org.eclipse.ditto.services.models.authorization.AuthorizationMessagingConstants.DISPATCHER_ACTOR_PATH;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that dispatches commands not authorized by any entity.
 */
public final class DispatcherActor extends AbstractActor {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "dispatcherActor";

    /**
     * Path of {@code SearchActor}.
     */
    private static final String THINGS_SEARCH_ACTOR_PATH = "/user/thingsSearchRoot/thingsSearch";

    private final ActorRef pubSubMediator;
    private final ActorRef enforcerShardRegion;
    private final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer;
    private final DiagnosticLoggingAdapter log;

    private DispatcherActor(final ActorRef pubSubMediator, final ActorRef enforcerShardRegion,
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {
        this.pubSubMediator = pubSubMediator;
        this.enforcerShardRegion = enforcerShardRegion;
        this.preEnforcer = preEnforcer;
        log = LogUtil.obtain(this);
    }

    public static Props props(final ActorRef pubSubMediator,
            final ActorRef enforcerShardRegion,
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {

        return Props.create(DispatcherActor.class,
                () -> new DispatcherActor(pubSubMediator, enforcerShardRegion, preEnforcer));
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        sanityCheck(getSelf());
        putSelfToPubSubMediator(getSelf(), pubSubMediator);
    }

    @Override
    public Receive createReceive() {

        return ReceiveBuilder.create()
                // TODO: handle RetrieveThings
                .match(ThingSearchCommand.class, command -> forwardToThingSearchActor(command, getSender()))
                .matchAny(message -> {
                    log.warning("unknown message: <{}>", message);
                    unhandled(message);
                })
                .build();
    }

    private void forwardToThingSearchActor(final ThingSearchCommand command, final ActorRef sender) {
        final Send wrappedCommand = new Send(THINGS_SEARCH_ACTOR_PATH, command);
        pubSubMediator.tell(wrappedCommand, sender);
    }

    /**
     * Verify the actor path of self agrees with what is advertised in ditto-services-models-authorization
     *
     * @param self ActorRef of this actor.
     */
    private static void sanityCheck(final ActorRef self) {
        final String selfPath = self.path().toStringWithoutAddress();
        if (!Objects.equals(DISPATCHER_ACTOR_PATH, selfPath)) {
            final String message =
                    String.format("Path of <%s> is <%s>, which does not agree with the advertised path <%s>",
                            ACTOR_NAME, selfPath, DISPATCHER_ACTOR_PATH);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Tell PubSubMediator about self so that other actors may send messages here from other cluster nodes.
     *
     * @param self ActorRef of this actor.
     * @param pubSubMediator Akka PubSub mediator.
     */
    private static void putSelfToPubSubMediator(final ActorRef self, final ActorRef pubSubMediator) {
        pubSubMediator.tell(new Put(self), self);
    }
}
