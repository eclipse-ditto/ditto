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

import org.eclipse.ditto.services.concierge.util.config.AbstractConciergeConfigReader;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.ThingSearchSudoCommand;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
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
public final class DispatcherActor extends AbstractGraphActor<DispatcherActor.Dispatch> {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "dispatcherActor";

    private final Sink<Dispatch, ?> handler;
    private final ActorRef thingsAggregatorActor;
    private DiagnosticLoggingAdapter log;

    private DispatcherActor(final AbstractConciergeConfigReader configReader,
            final ActorRef enforcerShardRegion,
            final ActorRef pubSubMediator,
            final Sink<Dispatch, ?> handler) {
        this.handler = handler;
        final Props props = ThingsAggregatorActor.props(configReader, enforcerShardRegion);
        thingsAggregatorActor = getContext().actorOf(props, ThingsAggregatorActor.ACTOR_NAME);
        log = LogUtil.obtain(this);

        initActor(getSelf(), pubSubMediator);
    }

    @Override
    protected Class<Dispatch> getMessageClass() {
        return Dispatch.class;
    }

    @Override
    protected Source<Dispatch, ?> mapMessage(final Object message) {
        return Source.single(new Dispatch(message, getSender(), thingsAggregatorActor, log));
    }

    @Override
    protected Sink<Dispatch, ?> getHandler() {
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

        final Graph<FlowShape<Dispatch, Dispatch>, NotUsed> dispatchFlow = createDispatchFlow(pubSubMediator);

        final Sink<Dispatch, NotUsed> handler = asContextualFlow(preEnforcer)
                .via(dispatchFlow)
                .to(Sink.foreach(dispatch -> dispatch.log.warning("Unhandled: <{}>", dispatch.message)));

        return Props.create(DispatcherActor.class,
                () -> new DispatcherActor(configReader, enforcerShardRegion, pubSubMediator, handler));
    }

    /**
     * Create a stream to dispatch search and things commands.
     *
     * @param pubSubMediator Akka pub-sub-mediator.
     * @return stream to dispatch search and thing commands.
     */
    private static Graph<FlowShape<Dispatch, Dispatch>, NotUsed> createDispatchFlow(final ActorRef pubSubMediator) {
        return GraphDSL.create(builder -> {

            final FanOutShape2<Dispatch, Dispatch, Dispatch> multiplexSearch =
                    builder.add(multiplexBy(ThingSearchCommand.class, ThingSearchSudoCommand.class));

            final FanOutShape2<Dispatch, Dispatch, Dispatch> multiplexRetrieveThings =
                    builder.add(multiplexBy(RetrieveThings.class, SudoRetrieveThings.class));

            final SinkShape<Dispatch> forwardToSearchActor = builder.add(searchActorSink(pubSubMediator));

            final SinkShape<Dispatch> forwardToThingsAggregator = builder.add(thingsAggregatorSink());

            builder.from(multiplexSearch.out0()).to(forwardToSearchActor);
            builder.from(multiplexRetrieveThings.out0()).to(forwardToThingsAggregator);
            builder.from(multiplexSearch.out1()).toInlet(multiplexRetrieveThings.in());

            return FlowShape.of(multiplexSearch.in(), multiplexRetrieveThings.out1());
        });
    }

    private static Graph<FanOutShape2<Dispatch, Dispatch, Dispatch>, NotUsed> multiplexBy(final Class<?>... classes) {
        return Filter.multiplexBy(dispatch ->
                Arrays.stream(classes).anyMatch(clazz -> clazz.isInstance(dispatch.getMessage()))
                        ? Optional.of(dispatch)
                        : Optional.empty());
    }

    private static Sink<Dispatch, ?> searchActorSink(final ActorRef pubSubMediator) {
        return Sink.foreach(dispatch -> pubSubMediator.tell(
                new DistributedPubSubMediator.Send(SEARCH_ACTOR_PATH, dispatch.getMessage()),
                dispatch.getSender()));
    }

    private static Sink<Dispatch, ?> thingsAggregatorSink() {
        return Sink.foreach(dispatch ->
                dispatch.thingsAggregatorActor.tell(dispatch.getMessage(), dispatch.getSender()));
    }

    private static Flow<Dispatch, Dispatch, NotUsed> asContextualFlow(
            final Graph<FlowShape<WithSender, WithSender>, ?> preEnforcer) {

        return Flow.<Dispatch>create().flatMapConcat(dispatch ->
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

    public static final class Dispatch implements WithSender<Object> {

        private final Object message;
        private final ActorRef sender;
        private final ActorRef thingsAggregatorActor;
        private final DiagnosticLoggingAdapter log;

        private Dispatch(final Object message, final ActorRef sender, final ActorRef thingsAggregatorActor,
                final DiagnosticLoggingAdapter log) {
            this.message = message;
            this.sender = sender;
            this.thingsAggregatorActor = thingsAggregatorActor;
            this.log = log;
        }

        @Override
        public Object getMessage() {
            return message;
        }

        @Override
        public ActorRef getSender() {
            return sender;
        }

        private Dispatch replaceMessage(final WithSender withSender) {
            return new Dispatch(withSender.getMessage(), sender, thingsAggregatorActor, log);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> WithSender<S> withMessage(final S newMessage) {
            return (WithSender<S>) new Dispatch(newMessage, sender, thingsAggregatorActor, log);
        }
    }
}
