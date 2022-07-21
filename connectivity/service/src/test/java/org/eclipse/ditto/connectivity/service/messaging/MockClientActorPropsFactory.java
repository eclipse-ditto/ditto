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
package org.eclipse.ditto.connectivity.service.messaging;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionIdInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.RecoveryStatus;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CheckConnectionLogsActive;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.DeleteConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.EnableConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionStatus;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import com.typesafe.config.Config;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;

@SuppressWarnings("unused")
public final class MockClientActorPropsFactory implements ClientActorPropsFactory {

    /**
     * @param actorSystem the actor system in which to load the extension.
     * @param config the config the extension is configured.
     */
    public MockClientActorPropsFactory(final ActorSystem actorSystem, final Config config) {
    }

    @Override
    public Props getActorPropsForType(final Connection connection, final ActorRef commandForwarderActor,
            final ActorRef connectionActor,
            final ActorSystem actorSystem, final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {

        return MockClientActor.props();
    }

    /**
     * Mocks a {@link org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceActor} and provides abstraction for a real connection.
     */
    public static class MockClientActor extends AbstractActorWithStash {

        private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

        private final ActorRef mediator = DistributedPubSub.get(getContext().getSystem()).mediator();
        @Nullable private ActorRef delegate;
        @Nullable private ActorRef gossip;

        private MockClientActor() {
        }

        public static Props props() {
            return Props.create(MockClientActor.class);
        }

        @Override
        public void preStart() {
            log.info("Mock client actor started.");
            if (gossip != null) {
                gossip.tell(getSelf(), getSelf());
            }
            subscribeForSnapshotPubSubTopic(mediator);
        }

        private void subscribeForSnapshotPubSubTopic(final ActorRef pubSubMediator) {
            final var self = getSelf();
            final var subscriptionMessage =
                    DistPubSubAccess.subscribe("mockClientActor:change", self);
            pubSubMediator.tell(subscriptionMessage, self);
        }

        @Override
        public void postStop() {
            log.info("Mock client actor was stopped.");
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                            mediator.tell(DistPubSubAccess.publish("mockClientActor:subscribed", new Subscribed()),
                                    getSelf()))
                    .match(ChangeActorRef.class, s -> {
                        delegate =
                                s.delegate != null ? getContext().getSystem().provider().resolveActorRef(s.delegate) :
                                        null;
                        gossip =
                                s.gossip != null ? getContext().getSystem().provider().resolveActorRef(s.gossip) : null;
                        if (gossip != null) {
                            gossip.tell(getSelf(), getSelf());
                        }
                        getSender().tell(new ActorRefChanged(), getSelf());
                        log.info("Switching state.");
                        getContext().become(initializedBehavior(), false);
                        unstashAll();
                    })
                    .matchAny(any -> stash())
                    .build();
        }

        private Receive initializedBehavior() {
            return receiveBuilder()
                    .match(CreateConnection.class, cc -> {
                        log.info("Creating connection...");
                        forward(cc);
                        sender().tell(new Status.Success("mock"), getSelf());
                    })
                    .match(OpenConnection.class, oc -> {
                        log.info("Opening connection...");
                        forward(oc);
                        sender().tell(new Status.Success("mock"), getSelf());
                    })
                    .match(ModifyConnection.class, mc -> {
                        log.info("Modifying connection...");
                        forward(mc);
                        sender().tell(new Status.Success("mock"), getSelf());
                    })
                    .match(CloseConnection.class, cc -> {
                        log.info("Closing connection...");
                        forward(cc);
                        sender().tell(new Status.Success("mock"), getSelf());
                    })
                    .match(DeleteConnection.class, dc -> {
                        log.info("Deleting connection...");
                        forward(dc);
                        sender().tell(new Status.Success("mock"), getSelf());
                    })
                    .match(RetrieveConnectionStatus.class, rcs -> {
                        log.info("Retrieve connection status...");
                        sender().tell(ConnectivityModelFactory.newClientStatus("client1",
                                        ConnectivityStatus.OPEN, RecoveryStatus.SUCCEEDED, "connection is open",
                                        TestConstants.INSTANT),
                                getSelf());

                        // simulate consumer and pusblisher actor response
                        sender().tell(ConnectivityModelFactory.newSourceStatus("client1",
                                        ConnectivityStatus.OPEN, "source1", "consumer started"),
                                getSelf());
                        sender().tell(ConnectivityModelFactory.newSourceStatus("client1",
                                        ConnectivityStatus.OPEN, "source2", "consumer started"),
                                getSelf());
                        sender().tell(ConnectivityModelFactory.newTargetStatus("client1",
                                        ConnectivityStatus.OPEN, "target1", "publisher started"),
                                getSelf());
                        sender().tell(ConnectivityModelFactory.newTargetStatus("client1",
                                        ConnectivityStatus.OPEN, "target2", "publisher started"),
                                getSelf());
                        sender().tell(ConnectivityModelFactory.newTargetStatus("client1",
                                        ConnectivityStatus.OPEN, "target3", "publisher started"),
                                getSelf());
                    })
                    .match(RetrieveConnectionLogs.class, rcl -> {
                        log.info("Retrieve connection logs...");
                        // forwarding to delegate so it can respond to correct sender
                        if (null != delegate) {
                            delegate.forward(rcl, getContext());
                        } else {
                            log.error(
                                    "No delegate found in MockClientActor. RetrieveConnectionLogs needs a delegate which" +
                                            " needs to respond with a RetrieveConnectionLogsResponse to the sender of the command");
                        }
                    })
                    .match(EnableConnectionLogs.class, ecl -> {
                        log.info("Enable connection logs...");
                        forward(ecl);
                    })
                    .match(TestConnection.class, testConnection -> {
                        log.info("Testing connection");
                        final DittoRuntimeException exception =
                                ConnectionIdInvalidException.newBuilder("invalid").build();
                        if (testConnection.getDittoHeaders().getOrDefault("error", "").equals("true")) {
                            sender().tell(exception, getSelf());
                        }

                        if (testConnection.getDittoHeaders().getOrDefault("fail", "").equals("true")) {
                            sender().tell(new Status.Failure(exception), getSelf());
                        }

                        sender().tell(new Status.Success("mock"), getSelf());
                    })
                    .match(CheckConnectionLogsActive.class, ccla -> {
                        log.info("Check connection logs active...");
                        forward(ccla);
                    })
                    .match(ActorRef.class, actorRef -> {})
                    .matchAny(unhandled -> {
                        log.info("Received unhandled message: {}", unhandled.getClass().getName());
                        forward(unhandled);
                    })
                    .build();
        }

        private void forward(final Object obj) {
            if (delegate != null) {
                delegate.tell(obj, getSelf());
            } else {
                log.info("Not forwarding, since no delegate is present.");
            }
        }

        public static class ChangeActorRef implements Jsonifiable<JsonObject> {

            final String delegate;
            @Nullable final String gossip;

            public ChangeActorRef(@Nullable final String delegate, @Nullable final String gossip) {
                this.delegate = delegate;
                this.gossip = gossip;
            }

            @Override
            public JsonObject toJson() {
                final var jsonBuilder = JsonObject.newBuilder();
                if (null != delegate) {
                    jsonBuilder.set("delegate", delegate);
                }
                if (null != gossip) {
                    jsonBuilder.set("gossip", gossip);
                }
                return jsonBuilder.build();
            }

            public static ChangeActorRef fromJson(final JsonObject jsonObject) {
                return new ChangeActorRef(jsonObject.getValue("delegate").map(JsonValue::asString).orElse(null),
                        jsonObject.getValue("gossip").map(JsonValue::asString).orElse(null));
            }

        }

        public static class ActorRefChanged implements Jsonifiable<JsonObject> {

            public ActorRefChanged() {
            }

            @Override
            public JsonObject toJson() {
                return JsonObject.empty();
            }

            public static ActorRefChanged fromJson(final JsonObject jsonObject) {
                return new ActorRefChanged();
            }
        }

        public static class Subscribed implements Jsonifiable<JsonObject> {

            public Subscribed() {
            }

            @Override
            public JsonObject toJson() {
                return JsonObject.empty();
            }

            public static Subscribed fromJson(final JsonObject jsonObject) {
                return new Subscribed();
            }
        }
    }

}
