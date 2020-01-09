/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.signalenrichment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;

/**
 * This actor subscribes via Distributed pub/sub to {@link #POLICY_ID_TOPIC} on which the {@code PolicyPersistenceActor}
 * publishes messages of type {@link PolicyId} whenever a policy was modified.
 */
public class PolicyObserverActor extends AbstractActor {

    /**
     * The name of this actor in the actor system.
     */
    public static final String ACTOR_NAME = "policyObserver";

    private static final String POLICY_ID_TOPIC = PolicyEvent.TYPE_PREFIX + "id";

    private final DiagnosticLoggingAdapter logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final Collection<Consumer<PolicyId>> consumers;

    @SuppressWarnings("unused")
    private PolicyObserverActor(final ActorRef pubSubMediator) {

        consumers = new ArrayList<>();
        pubSubMediator.tell(DistPubSubAccess.subscribe(POLICY_ID_TOPIC, getSelf()),
                getSelf());
    }

    /**
     * Creates Akka configuration object {@link akka.actor.Props} for this PolicyObserverActor.
     *
     * @param pubSubMediator the PubSub mediator actor.
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef pubSubMediator) {

        return Props.create(PolicyObserverActor.class, pubSubMediator);
    }

    @Override
    public Receive createReceive() {

        return receiveBuilder()
                .match(PolicyTag.class, policyTag -> {
                    final PolicyId policyId = policyTag.getEntityId();
                    consumers.forEach(consumer -> consumer.accept(policyId));
                })
                .match(AddObserver.class, addObserver -> consumers.add(addObserver.consumer))
                .matchAny(m -> logger.warning("Unhandled message: <{}>", m))
                .build();
    }

    /**
     * Message handled by the {@link PolicyObserverActor} which adds adding a {@link Consumer} for {@link PolicyId}
     * messages to which the PolicyObserverActor subscribes via pub/sub.
     */
    public static final class AddObserver {

        private final Consumer<PolicyId> consumer;

        private AddObserver(final Consumer<PolicyId> consumer) {
            this.consumer = consumer;
        }

        /**
         * Creates a new message adding a {@link Consumer} for {@link PolicyId}
         * messages to which the PolicyObserverActor subscribes via pub/sub.
         *
         * @param consumer the Consumer to get PolicyId messages.
         * @return the created message.
         */
        public static AddObserver of(final Consumer<PolicyId> consumer) {
            return new AddObserver(consumer);
        }
    }
}
