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
package org.eclipse.ditto.services.concierge.starter.actors;

import static akka.cluster.pubsub.DistributedPubSubMediator.Put;
import static akka.cluster.pubsub.DistributedPubSubMediator.Send;
import static org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants.DISPATCHER_ACTOR_PATH;

import java.util.Objects;

import org.eclipse.ditto.services.models.thingsearch.commands.sudo.ThingSearchSudoCommand;
import org.eclipse.ditto.services.utils.akka.controlflow.Consume;
import org.eclipse.ditto.services.utils.akka.controlflow.FanIn;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.GraphActor;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.FanInShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.stage.GraphStage;

/**
 * Actor that dispatches commands not authorized by any entity.
 */
public final class DispatcherActor {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "dispatcherActor";

    /**
     * Path of {@code SearchActor}.
     */
    private static final String THINGS_SEARCH_ACTOR_PATH = "/user/thingsSearchRoot/thingsSearch";

    public static Props props(final ActorRef pubSubMediator,
            final ActorRef enforcerShardRegion,
            final GraphStage<FlowShape<WithSender, WithSender>> preEnforcer) {

        return GraphActor.partial(actorContext -> {
            sanityCheck(actorContext.self());
            putSelfToPubSubMediator(actorContext.self(), pubSubMediator);
            return Flow.<WithSender>create()
                    .via(preEnforcer)
                    .via(dispatchGraph(actorContext, pubSubMediator, enforcerShardRegion));
        });
    }

    /**
     * Create an Akka stream graph to dispatch {@code RetrieveThings} and {@code ThingSearchCommand}.
     *
     * @param actorContext context of this actor.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param enforcerShardRegion shard region of enforcer actors.
     * @return Akka stream graph to dispatch {@code RetrieveThings} and {@code ThingSearchCommand}.
     */
    public static Graph<FlowShape<WithSender, WithSender>, NotUsed> dispatchGraph(
            final AbstractActor.ActorContext actorContext,
            final ActorRef pubSubMediator,
            final ActorRef enforcerShardRegion) {

        // TODO: handle RetireveThings
        return dispatchSearchCommands(pubSubMediator);
    }

    /**
     * Create a graph to dispatch search commands and sudo search commands.
     * <pre>
     * {@code
     *              +-----------------------+ output       +-----+
     * input +----->+searchCommandFilter    +------------->+     |
     *              +-----------+-----------+          in0 |     |
     *                          |                          |     |
     *                          | unhandled                |     | out       +--------------------+
     *       +------------------+                          |fanIn+---------->+forwardToSearchActor|
     *       |                                             |     |           +--------------------+
     *       |                                             |     |
     *       |      +-----------------------+ output       |     |
     *       +----->+sudoSearchCommandFilter+------------->+     |
     *       input  +-----------+-----------+          in1 +-----+
     *                          |
     *                          | unhandled
     *                          |
     *                          v
     * }
     * </pre>
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @return graph of
     */
    public static Graph<FlowShape<WithSender, WithSender>, NotUsed> dispatchSearchCommands(
            final ActorRef pubSubMediator) {

        return GraphDSL.create(builder -> {
            final Filter<ThingSearchCommand> searchCommandFilter = Filter.of(ThingSearchCommand.class);
            final Filter<ThingSearchSudoCommand> sudoSearchCommandFilter = Filter.of(ThingSearchSudoCommand.class);
            builder.add(searchCommandFilter);
            builder.add(sudoSearchCommandFilter);

            final FanInShape2<WithSender<ThingSearchCommand>,
                    WithSender<ThingSearchSudoCommand>,
                    WithSender<Object>> fanIn = builder.add(FanIn.of2());

            final SinkShape<WithSender<Object>> forwardToSearchActor =
                    builder.add(forwardToThingSearchActor(pubSubMediator));

            builder.from(searchCommandFilter.output).toInlet(fanIn.in0());
            builder.from(searchCommandFilter.unhandled).toInlet(sudoSearchCommandFilter.input);
            builder.from(sudoSearchCommandFilter.output).toInlet(fanIn.in1());
            builder.from(fanIn.out()).to(forwardToSearchActor);

            return FlowShape.of(searchCommandFilter.input, sudoSearchCommandFilter.unhandled);
        });
    }

    private static Consume<Object> forwardToThingSearchActor(final ActorRef pubSubMediator) {
        return Consume.of((message, sender) -> {
            final Send wrappedCommand = new Send(THINGS_SEARCH_ACTOR_PATH, message);
            pubSubMediator.tell(wrappedCommand, sender);
        });
    }

    /**
     * Verify the actor path of self agrees with what is advertised in ditto-services-models-concierge.
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
