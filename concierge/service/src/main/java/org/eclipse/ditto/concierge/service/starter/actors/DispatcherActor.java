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
package org.eclipse.ditto.concierge.service.starter.actors;

import static org.eclipse.ditto.concierge.api.ConciergeMessagingConstants.DISPATCHER_ACTOR_PATH;
import static org.eclipse.ditto.thingsearch.api.ThingsSearchConstants.SEARCH_ACTOR_PATH;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.concierge.service.starter.DittoConciergeConfig;
import org.eclipse.ditto.internal.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.internal.utils.akka.controlflow.Filter;
import org.eclipse.ditto.internal.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.policies.enforcement.PreEnforcer;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.thingsearch.api.commands.sudo.ThingSearchSudoCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Sink;

/**
 * Actor that dispatches signals not authorized by any entity meaning signals without entityId.
 * TODO TJ candidate for removal - but needs to be replaced by other means
 */
public final class DispatcherActor
        extends AbstractGraphActor<DispatcherActor.ImmutableDispatch, DittoHeadersSettable<?>> {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "dispatcherActor";

    private static final Map<String, ThreadSafeDittoLogger> NAMESPACE_INSPECTION_LOGGERS = new HashMap<>();

    private final Flow<ImmutableDispatch, ImmutableDispatch, NotUsed> handler;
    private final ActorRef thingsAggregatorActor;
    private final EnforcementConfig enforcementConfig;

    @SuppressWarnings("unused")
    private DispatcherActor(final ActorRef conciergeForwarder,
            final ActorRef pubSubMediator,
            final Flow<ImmutableDispatch, ImmutableDispatch, NotUsed> handler) {

        super(WithDittoHeaders.class, UnaryOperator.identity());

        enforcementConfig = DittoConciergeConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        ).getEnforcementConfig();

        enforcementConfig.getSpecialLoggingInspectedNamespaces()
                .forEach(loggedNamespace -> NAMESPACE_INSPECTION_LOGGERS.put(
                        loggedNamespace,
                        DittoLoggerFactory.getThreadSafeLogger(DispatcherActor.class.getName() +
                                ".namespace." + loggedNamespace)));

        this.handler = handler;
        final Props props = ThingsAggregatorActor.props(conciergeForwarder);
        thingsAggregatorActor = getContext().actorOf(props, ThingsAggregatorActor.ACTOR_NAME);

        initActor(getSelf(), pubSubMediator);
    }

    @Override
    protected ImmutableDispatch mapMessage(final DittoHeadersSettable<?> message) {
        return new ImmutableDispatch(message, getSender(), thingsAggregatorActor);
    }

    @Override
    protected Sink<ImmutableDispatch, ?> createSink() {
        return handler.to(
                Sink.foreach(dispatch -> logger.withCorrelationId(dispatch.getMessage())
                        .warning("Unhandled Message in DispatcherActor: <{}>", dispatch))
        );
    }

    @Override
    protected int getBufferSize() {
        return 42; // TODO TJ remove
    }

    /**
     * Create Akka actor configuration Props object without pre-enforcer.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param enforcerActor address of the enforcer actor.
     * @return the Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ActorRef enforcerActor) {
        return props(pubSubMediator, enforcerActor, CompletableFuture::completedFuture);
    }

    /**
     * Create Akka actor configuration Props object with pre-enforcer.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param conciergeForwarder the address of the concierge forwarder actor.
     * @param preEnforcer the pre-enforcer as graph.
     * @return the Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final PreEnforcer preEnforcer) {

        final Flow<ImmutableDispatch, ImmutableDispatch, NotUsed> dispatchFlow =
                Flow.fromGraph(createDispatchFlow(pubSubMediator, preEnforcer));

        return Props.create(DispatcherActor.class, conciergeForwarder, pubSubMediator, dispatchFlow);
    }

    /**
     * Create a stream to dispatch search and things commands.
     *
     * @param pubSubMediator Akka pub-sub-mediator.
     * @return stream to dispatch search and thing commands.
     */
    private static Graph<FlowShape<ImmutableDispatch, ImmutableDispatch>, NotUsed> createDispatchFlow(
            final ActorRef pubSubMediator,
            final PreEnforcer preEnforcer) {

        return GraphDSL.create(builder -> {
            final FanOutShape2<ImmutableDispatch, ImmutableDispatch, ImmutableDispatch> multiplexSearch =
                    builder.add(multiplexBy(ThingSearchCommand.class, ThingSearchSudoCommand.class));

            final FanOutShape2<ImmutableDispatch, ImmutableDispatch, ImmutableDispatch> multiplexRetrieveThings =
                    builder.add(multiplexBy(RetrieveThings.class, SudoRetrieveThings.class));

            final SinkShape<ImmutableDispatch> forwardToSearchActor =
                    builder.add(searchActorSink(pubSubMediator, preEnforcer));

            final SinkShape<ImmutableDispatch> forwardToThingsAggregator =
                    builder.add(thingsAggregatorSink(preEnforcer));

            builder.from(multiplexSearch.out0()).to(forwardToSearchActor);
            builder.from(multiplexRetrieveThings.out0()).to(forwardToThingsAggregator);
            builder.from(multiplexSearch.out1()).toInlet(multiplexRetrieveThings.in());

            return FlowShape.of(multiplexSearch.in(), multiplexRetrieveThings.out1());
        });
    }

    private static Graph<FanOutShape2<ImmutableDispatch, ImmutableDispatch, ImmutableDispatch>, NotUsed> multiplexBy(
            final Class<?>... classes) {

        return Filter.multiplexBy(dispatch ->
                Arrays.stream(classes).anyMatch(clazz -> clazz.isInstance(dispatch.getMessage()))
                        ? Optional.of(dispatch)
                        : Optional.empty());
    }

    private static Sink<ImmutableDispatch, ?> searchActorSink(final ActorRef pubSubMediator,
            final PreEnforcer preEnforcer) {
        return Sink.foreach(dispatchToPreEnforce ->
                preEnforce(dispatchToPreEnforce, preEnforcer, dispatch -> {
                    final DittoHeadersSettable<?> command = dispatch.message;
                    if (command instanceof ThingSearchCommand) {
                        final ThingSearchCommand<?> searchCommand = (ThingSearchCommand<?>) command;
                        final Set<String> namespaces = searchCommand.getNamespaces().orElseGet(Set::of);

                        NAMESPACE_INSPECTION_LOGGERS.entrySet().stream()
                                .filter(entry -> namespaces.contains(entry.getKey()))
                                .map(Map.Entry::getValue)
                                .forEach(l -> {
                                    if (searchCommand instanceof ThingSearchQueryCommand) {
                                        final String filter = ((ThingSearchQueryCommand<?>) searchCommand)
                                                .getFilter().orElse(null);
                                        l.withCorrelationId(command).info(
                                                "Forwarding search query command type <{}> with filter <{}> and " +
                                                        "fields <{}>",
                                                searchCommand.getType(),
                                                filter,
                                                searchCommand.getSelectedFields().orElse(null));
                                    }
                                });
                    }
                    pubSubMediator.tell(
                            DistPubSubAccess.send(SEARCH_ACTOR_PATH, dispatch.getMessage()),
                            dispatch.getSender());
                })
        );
    }

    private static Sink<ImmutableDispatch, ?> thingsAggregatorSink(final PreEnforcer preEnforcer) {
        return Sink.foreach(dispatchToPreEnforce ->
                preEnforce(dispatchToPreEnforce, preEnforcer, dispatch ->
                        dispatch.thingsAggregatorActor.tell(dispatch.getMessage(), dispatch.getSender())
                )
        );
    }

    private static void preEnforce(final ImmutableDispatch dispatch,
            final PreEnforcer preEnforcer,
            final Consumer<ImmutableDispatch> andThen) {
        preEnforcer.withErrorHandlingAsync(dispatch, Done.done(), newDispatch -> {
            andThen.accept(newDispatch);
            return CompletableFuture.completedStage(Done.done());
        });
    }

    private static void initActor(final ActorRef self, final ActorRef pubSubMediator) {
        sanityCheck(self);
        putSelfToPubSubMediator(self, pubSubMediator);
    }

    /**
     * Verify the actor path of self agrees with what is advertised in ditto-concierge-api.
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
        pubSubMediator.tell(DistPubSubAccess.put(self), self);
    }

    /**
     * Local immutable implementation of {@link WithSender} containing an additional {@code thingsAggregatorActor}
     * reference.
     */
    @Immutable
    static final class ImmutableDispatch implements WithSender<DittoHeadersSettable<?>> {

        private final DittoHeadersSettable<?> message;
        private final ActorRef sender;
        private final ActorRef thingsAggregatorActor;

        private ImmutableDispatch(final DittoHeadersSettable<?> message, final ActorRef sender,
                final ActorRef thingsAggregatorActor) {

            this.message = message;
            this.sender = sender;
            this.thingsAggregatorActor = thingsAggregatorActor;
        }

        @Override
        public DittoHeadersSettable<?> getMessage() {
            return message;
        }

        @Override
        public ActorRef getSender() {
            return sender;
        }

        @Override
        public ImmutableDispatch withMessage(final DittoHeadersSettable<?> newMessage) {
            return new ImmutableDispatch(newMessage, sender, thingsAggregatorActor);
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
