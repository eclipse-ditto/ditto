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
package org.eclipse.ditto.services.concierge.starter.actors;

import static org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants.DISPATCHER_ACTOR_PATH;
import static org.eclipse.ditto.services.models.thingsearch.ThingsSearchConstants.SEARCH_ACTOR_PATH;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.concierge.util.config.AbstractConciergeConfigReader;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.ThingSearchSudoCommand;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor that dispatches signals not authorized by any entity meaning signals without entityId.
 */
public final class DispatcherActor extends AbstractGraphActor<DispatcherActor.ImmutableDispatch> {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "dispatcherActor";

    private final Flow<ImmutableDispatch, ImmutableDispatch, NotUsed> handler;
    private final ActorRef thingsAggregatorActor;

    private DispatcherActor(final AbstractConciergeConfigReader configReader,
            final ActorRef enforcerShardRegion,
            final ActorRef pubSubMediator,
            final Flow<ImmutableDispatch, ImmutableDispatch, NotUsed> handler) {
        this.handler = handler;
        final Props props = ThingsAggregatorActor.props(configReader, enforcerShardRegion);
        thingsAggregatorActor = getContext().actorOf(props, ThingsAggregatorActor.ACTOR_NAME);

        initActor(getSelf(), pubSubMediator);
    }

    @Override
    protected Class<ImmutableDispatch> getMessageClass() {
        return ImmutableDispatch.class;
    }

    @Override
    protected Source<ImmutableDispatch, NotUsed> mapMessage(final Object message) {
        return Source.single(new ImmutableDispatch(message, getSender(), thingsAggregatorActor));
    }

    @Override
    protected Flow<ImmutableDispatch, ImmutableDispatch, NotUsed> getHandler() {
        return handler;
    }

    /**
     * Create Akka actor configuration Props object without pre-enforcer.
     *
     * @param configReader the configReader for the concierge service.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param enforcerShardRegion shard region of enforcer actors.
     * @return the Props object.
     */
    public static Props props(final AbstractConciergeConfigReader configReader, final ActorRef pubSubMediator,
            final ActorRef enforcerShardRegion) {

        return props(configReader, pubSubMediator, enforcerShardRegion, Flow.create());
    }

    /**
     * Create Akka actor configuration Props object with pre-enforcer.
     *
     * @param configReader the configReader for the concierge service.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param enforcerShardRegion shard region of enforcer actors.
     * @param preEnforcer the pre-enforcer as graph.
     * @return the Props object.
     */
    public static Props props(final AbstractConciergeConfigReader configReader,
            final ActorRef pubSubMediator,
            final ActorRef enforcerShardRegion,
            final Graph<FlowShape<WithSender, WithSender>, ?> preEnforcer) {

        final Graph<FlowShape<ImmutableDispatch, ImmutableDispatch>, NotUsed> dispatchFlow = createDispatchFlow(pubSubMediator);

        final Flow<ImmutableDispatch, ImmutableDispatch, NotUsed> handler = asContextualFlow(preEnforcer)
                .via(dispatchFlow);

        return Props.create(DispatcherActor.class,
                () -> new DispatcherActor(configReader, enforcerShardRegion, pubSubMediator, handler));
    }

    /**
     * Create a stream to dispatch search and things commands.
     *
     * @param pubSubMediator Akka pub-sub-mediator.
     * @return stream to dispatch search and thing commands.
     */
    private static Graph<FlowShape<ImmutableDispatch, ImmutableDispatch>, NotUsed> createDispatchFlow(final ActorRef pubSubMediator) {
        return GraphDSL.create(builder -> {

            final FanOutShape2<ImmutableDispatch, ImmutableDispatch, ImmutableDispatch> multiplexSearch =
                    builder.add(multiplexBy(ThingSearchCommand.class, ThingSearchSudoCommand.class));

            final FanOutShape2<ImmutableDispatch, ImmutableDispatch, ImmutableDispatch> multiplexRetrieveThings =
                    builder.add(multiplexBy(RetrieveThings.class, SudoRetrieveThings.class));

            final SinkShape<ImmutableDispatch> forwardToSearchActor = builder.add(searchActorSink(pubSubMediator));

            final SinkShape<ImmutableDispatch> forwardToThingsAggregator = builder.add(thingsAggregatorSink());

            builder.from(multiplexSearch.out0()).to(forwardToSearchActor);
            builder.from(multiplexRetrieveThings.out0()).to(forwardToThingsAggregator);
            builder.from(multiplexSearch.out1()).toInlet(multiplexRetrieveThings.in());

            return FlowShape.of(multiplexSearch.in(), multiplexRetrieveThings.out1());
        });
    }

    private static Graph<FanOutShape2<ImmutableDispatch, ImmutableDispatch, ImmutableDispatch>, NotUsed> multiplexBy(final Class<?>... classes) {
        return Filter.multiplexBy(dispatch ->
                Arrays.stream(classes).anyMatch(clazz -> clazz.isInstance(dispatch.getMessage()))
                        ? Optional.of(dispatch)
                        : Optional.empty());
    }

    private static Sink<ImmutableDispatch, ?> searchActorSink(final ActorRef pubSubMediator) {
        return Sink.foreach(dispatch -> pubSubMediator.tell(
                new DistributedPubSubMediator.Send(SEARCH_ACTOR_PATH, dispatch.getMessage()),
                dispatch.getSender()));
    }

    private static Sink<ImmutableDispatch, ?> thingsAggregatorSink() {
        return Sink.foreach(dispatch ->
                dispatch.thingsAggregatorActor.tell(dispatch.getMessage(), dispatch.getSender()));
    }

    private static Flow<ImmutableDispatch, ImmutableDispatch, NotUsed> asContextualFlow(
            final Graph<FlowShape<WithSender, WithSender>, ?> preEnforcer) {

        return Flow.<ImmutableDispatch>create().flatMapConcat(dispatch ->
                Source.<WithSender>single(dispatch).via(preEnforcer).map(dispatch::replaceMessage));
    }

    private static void initActor(final ActorRef self, final ActorRef pubSubMediator) {
        sanityCheck(self);
        putSelfToPubSubMediator(self, pubSubMediator);
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
        pubSubMediator.tell(new DistributedPubSubMediator.Put(self), self);
    }

    /**
     * Local immutable implementation of {@link WithSender} containing an additional {@code thingsAggregatorActor}
     * reference.
     */
    @Immutable
    static final class ImmutableDispatch implements WithSender<Object> {

        private final Object message;
        private final ActorRef sender;
        private final ActorRef thingsAggregatorActor;

        private ImmutableDispatch(final Object message, final ActorRef sender, final ActorRef thingsAggregatorActor) {
            this.message = message;
            this.sender = sender;
            this.thingsAggregatorActor = thingsAggregatorActor;
        }

        @Override
        public Object getMessage() {
            return message;
        }

        @Override
        public ActorRef getSender() {
            return sender;
        }

        private ImmutableDispatch replaceMessage(final WithSender withSender) {
            return new ImmutableDispatch(withSender.getMessage(), sender, thingsAggregatorActor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> WithSender<S> withMessage(final S newMessage) {
            return (WithSender<S>) new ImmutableDispatch(newMessage, sender, thingsAggregatorActor);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ImmutableDispatch)) {
                return false;
            }
            final ImmutableDispatch that = (ImmutableDispatch) o;
            return Objects.equals(message, that.message) &&
                    Objects.equals(sender, that.sender) &&
                    Objects.equals(thingsAggregatorActor, that.thingsAggregatorActor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(message, sender, thingsAggregatorActor);
        }


        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "message=" + message +
                    ", sender=" + sender +
                    ", thingsAggregatorActor=" + thingsAggregatorActor +
                    "]";
        }
    }
}
