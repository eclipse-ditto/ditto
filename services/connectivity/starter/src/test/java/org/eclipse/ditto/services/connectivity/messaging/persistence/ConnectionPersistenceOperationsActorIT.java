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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.ConnectionSupervisorActor;
import org.eclipse.ditto.services.connectivity.messaging.DefaultClientActorPropsFactory;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoEventSourceITAssertions;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandInterceptor;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.Test;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;

/**
 * Tests {@link ConnectionPersistenceOperationsActor}.
 */
@AllValuesAreNonnullByDefault
public final class ConnectionPersistenceOperationsActorIT extends MongoEventSourceITAssertions<ConnectionId> {

    @Test
    public void purgeEntitiesWithoutNamespace() {
        assertPurgeEntitiesWithoutNamespace();
    }

    @Override
    protected String getServiceName() {
        return "connectivity";
    }

    @Override
    protected String getResourceType() {
        return ConnectivityCommand.RESOURCE_TYPE;
    }

    @Override
    protected ConnectionId toEntityId(final EntityId entityId) {
        return ConnectionId.of(entityId);
    }

    @Override
    protected Object getCreateEntityCommand(final ConnectionId id) {
        final AuthorizationContext authorizationContext =
                AuthorizationContext.newInstance(AuthorizationSubject.newInstance("subject"));
        final Source source =
                ConnectivityModelFactory.newSource(authorizationContext, "address");
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(id, ConnectionType.AMQP_091, ConnectivityStatus.CLOSED,
                        "amqp://user:pass@localhost:5671")
                        .sources(Collections.singletonList(source))
                        .build();
        return CreateConnection.of(connection, DittoHeaders.empty());
    }

    @Override
    protected Class<?> getCreateEntityResponseClass() {
        return CreateConnectionResponse.class;
    }

    @Override
    protected Object getRetrieveEntityCommand(final ConnectionId id) {
        return RetrieveConnection.of(id, DittoHeaders.empty());
    }

    @Override
    protected Class<?> getRetrieveEntityResponseClass() {
        return RetrieveConnectionResponse.class;
    }

    @Override
    protected Class<?> getEntityNotAccessibleClass() {
        return ConnectionNotAccessibleException.class;
    }

    @Override
    protected ActorRef startActorUnderTest(final ActorSystem actorSystem, final ActorRef pubSubMediator,
            final Config config) {

        final Props opsActorProps = ConnectionPersistenceOperationsActor.props(pubSubMediator, mongoDbConfig, config,
                persistenceOperationsConfig);
        return actorSystem.actorOf(opsActorProps, ConnectionPersistenceOperationsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator,
            final ConnectionId id) {
        // essentially never restart
        final TestProbe conciergeForwarderProbe = new TestProbe(system, "conciergeForwarder");
        final ConnectivityCommandInterceptor dummyInterceptor = command -> {};
        final ClientActorPropsFactory entityActorFactory = DefaultClientActorPropsFactory.getInstance();
        final Props props =
                ConnectionSupervisorActor.props(nopSub(), conciergeForwarderProbe.ref(), entityActorFactory,
                        dummyInterceptor);

        return system.actorOf(props, String.valueOf(id));
    }

    private static DittoProtocolSub nopSub() {
        return new DittoProtocolSub() {
            @Override
            public CompletionStage<Void> subscribe(final Collection<StreamingType> types,
                    final Collection<String> topics, final ActorRef subscriber) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void removeSubscriber(final ActorRef subscriber) {

            }

            @Override
            public CompletionStage<Void> updateLiveSubscriptions(
                    final Collection<StreamingType> types,
                    final Collection<String> topics, final ActorRef subscriber) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> removeTwinSubscriber(final ActorRef subscriber,
                    final Collection<String> topics) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }

}
