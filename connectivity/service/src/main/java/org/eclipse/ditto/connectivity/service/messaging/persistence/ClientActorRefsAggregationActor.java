/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.eclipse.ditto.connectivity.service.messaging.persistence.ClientActorRefsAggregationActor.AggregationState.AGGREGATED;
import static org.eclipse.ditto.connectivity.service.messaging.persistence.ClientActorRefsAggregationActor.AggregationState.AGGREGATING;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor;
import org.eclipse.ditto.connectivity.service.messaging.ClientActorRefs;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ClientActorRefsAggregationActor.AggregationData;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ClientActorRefsAggregationActor.AggregationState;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.slf4j.Logger;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Props;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.routing.Broadcast;

/**
 * This class regularly sends a {@link org.eclipse.ditto.connectivity.service.messaging.BaseClientActor.HealthSignal#PING ping}
 * signal to all client actors and expects a {@link org.eclipse.ditto.connectivity.service.messaging.BaseClientActor.HealthSignal#PONG pong} response.
 * This way all living client actor refs are determined and finally published to the {@link #receiver}.
 */
final class ClientActorRefsAggregationActor extends AbstractFSM<AggregationState, AggregationData> {

    private static final Logger LOGGER = DittoLoggerFactory.getLogger(ClientActorRefsAggregationActor.class);
    private static final String AGGREGATION_TIMEOUT_TIMER = "aggregationTimeout";
    private static final String START_AGGREGATION_TIMER = "startAggregation";

    private final Duration aggregationTimeout;
    private final Duration aggregationInterval;
    private final int clientCount;
    private final ActorRef clientActorRouter;
    private final ActorRef receiver;

    ClientActorRefsAggregationActor(final int clientCount,
            final ActorRef receiver,
            final ActorRef clientActorRouter,
            final Duration aggregationInterval,
            final Duration aggregationTimeout) {
        this.clientCount = clientCount;
        this.receiver = receiver;
        this.clientActorRouter = clientActorRouter;
        this.aggregationInterval = aggregationInterval;
        this.aggregationTimeout = aggregationTimeout;
    }

    /**
     * @param clientCount the expected number of clients
     * @param receiver the receiver of the aggregated {@link ClientActorRefs}.
     * @param clientActorRouter the client actor router used to send broadcasts to all clients.
     * @param aggregationInterval the interval for aggregation of client actor refs.
     * @param aggregationTimeout the maximum time the aggregation actor should wait for a response from all client actors.
     * @return the props of the aggregation actor.
     */
    static Props props(final int clientCount,
            final ActorRef receiver,
            final ActorRef clientActorRouter,
            final Duration aggregationInterval,
            final Duration aggregationTimeout) {
        return Props.create(ClientActorRefsAggregationActor.class, clientCount, receiver, clientActorRouter,
                aggregationInterval, aggregationTimeout);
    }

    private static Duration randomize(final Duration base) {
        return base.plus(Duration.ofMillis((long) (base.toMillis() * Math.random())));
    }

    @Override
    public void preStart() {
        when(AGGREGATED, inAggregatedState());
        when(AGGREGATING, inAggregatingState());

        startWith(AGGREGATED, AggregationData.initial());
        initialize();
        scheduleStartAggregationTimeout();
    }

    private FSMStateFunctionBuilder<AggregationState, AggregationData> inAggregatedState() {
        return matchEvent(StartAggregation.class, this::startAggregation)
                .anyEvent((event, data) -> {
                    LOGGER.warn("Received unexpected event in {} state.", AGGREGATED);
                    return stay();
                });
    }

    private FSMStateFunctionBuilder<AggregationState, AggregationData> inAggregatingState() {
        return matchEventEquals(BaseClientActor.HealthSignal.PONG, this::handleClientActorResponse)
                .eventEquals(StateTimeout(), (event, data) -> {
                    LOGGER.warn("Did not receive all client actor refs within timeout of {}. Received: {}.",
                            aggregationTimeout, data.clientActorRefs);
                    return goToAggregated();
                })
                .anyEvent((event, data) -> {
                    LOGGER.warn("Received unexpected event in {} state.", AGGREGATING);
                    return stay();
                });
    }

    private FSM.State<AggregationState, AggregationData> handleClientActorResponse(
            final BaseClientActor.HealthSignal pong, final AggregationData data) {
        final AggregationData newData;
        final ActorRef sender = sender();
        if (sender == null) {
            LOGGER.warn("Received PONG response without sender information. This seems to be a bug.");
            newData = data;
        } else {
            newData = data.addRef(sender);
        }
        if (newData.clientActorRefs.size() >= clientCount) {
            receiver.tell(newData.getClientActorRefs(), ActorRef.noSender());
            LOGGER.info("Finished aggregation of all client actor refs successfully.");
            return goToAggregated();
        } else {
            return stay().using(newData);
        }
    }

    private FSM.State<AggregationState, AggregationData> startAggregation(final StartAggregation startAggregation,
            final AggregationData data) {
        LOGGER.info("Starting aggregation of all client actor refs.");
        clientActorRouter.tell(new Broadcast(BaseClientActor.HealthSignal.PING), getSelf());
        scheduleAggregationTimeout();
        return goTo(AGGREGATING).using(data.reset());
    }

    private FSM.State<AggregationState, AggregationData> goToAggregated() {
        cancelAggregationTimeout();
        scheduleStartAggregationTimeout();
        return goTo(AGGREGATED);
    }

    private void scheduleAggregationTimeout() {
        startSingleTimer(AGGREGATION_TIMEOUT_TIMER, StateTimeout(), aggregationTimeout);
    }

    private void cancelAggregationTimeout() {
        cancelTimer(AGGREGATION_TIMEOUT_TIMER);
    }

    private void scheduleStartAggregationTimeout() {
        startSingleTimer(START_AGGREGATION_TIMER, StartAggregation.INSTANCE, randomize(aggregationInterval));
    }

    private static class StartAggregation {

        private static final StartAggregation INSTANCE = new StartAggregation();

        private StartAggregation() {}
    }

    enum AggregationState {
        AGGREGATING,
        AGGREGATED;
    }

    static class AggregationData {

        private final List<ActorRef> clientActorRefs;

        AggregationData(final List<ActorRef> clientActorRefs) {
            this.clientActorRefs = Collections.unmodifiableList(clientActorRefs);
        }

        static AggregationData initial() {
            return new AggregationData(List.of());
        }

        AggregationData reset() {
            return new AggregationData(List.of());
        }

        private AggregationData addRef(final ActorRef actorRef) {
            final ArrayList<ActorRef> newRefs = new ArrayList<>(clientActorRefs);
            newRefs.add(actorRef);
            return new AggregationData(newRefs);
        }

        private ClientActorRefs getClientActorRefs() {
            final ClientActorRefs clientActorRefs = ClientActorRefs.empty();
            clientActorRefs.add(this.clientActorRefs);
            return clientActorRefs;
        }

    }

}
