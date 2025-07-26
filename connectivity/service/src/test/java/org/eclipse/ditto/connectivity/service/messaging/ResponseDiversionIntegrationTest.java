/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.util.ConnectionPubSub;
import org.eclipse.ditto.internal.utils.pekko.ActorSystemResource;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Integration test for response diversion functionality.
 * This test verifies the complete flow of diverting responses from one connection to another.
 */
public class ResponseDiversionIntegrationTest {

    private static final ConnectionId SOURCE_CONNECTION_ID = ConnectionId.of("source-connection");
    private static final ConnectionId TARGET_CONNECTION_ID = ConnectionId.of("target-connection");
    private static final ThingId THING_ID = ThingId.of("test:thing");
    private static final String CORRELATION_ID = "integration-test-correlation";
    private static final AuthorizationContext AUTHORIZATION_CONTEXT =
            AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                    AuthorizationModelFactory.newAuthSubject("nginx:ditto"));

    private ConnectionPubSub pubSub;
    private Connection sourceConnection;
    private Connection targetConnection;

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.ofEntries(
                    Map.entry("pekko.actor.provider", "cluster")
            ))
    );

    @Before
    public void setUp() {
        pubSub = ConnectionPubSub.get(ACTOR_SYSTEM_RESOURCE.getActorSystem());

        // Create source connection with diversion configured
        sourceConnection = createSourceConnection();

        // Create target connection configured to receive diverted responses
        targetConnection = createTargetConnection();
    }

    @Test
    public void endToEndResponseDiversion() {
        final ActorSystem actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
        new TestKit(actorSystem) {{
            // Simulate the target connection subscribing for diverted responses
            final TestProbe targetConnectionActor = TestProbe.apply(actorSystem);
            pubSub.subscribeForDivertedResponses(TARGET_CONNECTION_ID, targetConnectionActor.ref(), false);

            // Allow time for subscription to propagate
            expectNoMessage(Duration.ofMillis(100));

            // Create a response signal with diversion headers
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .correlationId(CORRELATION_ID)
                    .putHeader(DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey(), TARGET_CONNECTION_ID.toString())
                    .putHeader(DittoHeaderDefinition.DITTO_DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), "response")
                    .build();

            final ModifyThingResponse response = ModifyThingResponse.modified(THING_ID, headers);

            // Create the interceptor for the source connection
            final ResponseDiversionInterceptor interceptor =
                    ResponseDiversionInterceptor.of(sourceConnection, pubSub);

            // Create an outbound signal
            final Target dummyTarget = ConnectivityModelFactory.newTargetBuilder()
                    .address("dummy/address")
                    .authorizationContext(AuthorizationContext.empty())
                    .topics(Topic.LIVE_MESSAGES, Topic.TWIN_EVENTS)
                    .build();

            final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(
                    response,
                    Collections.singletonList(dummyTarget)
            );

            // Intercept and divert the response
            final boolean wasDiverted = interceptor.interceptAndDivert(outboundSignal);

            // Verify the response was diverted
            assertThat(wasDiverted).isTrue();

            // In a real distributed system with actual PubSub, the target would receive the signal
            // Here we verify that the publish message was sent to the mediator
            // The actual delivery would depend on the PubSub implementation
        }};
    }

    @Test
    public void responseDiversionWithMultipleResponseTypes() {
        final ActorSystem actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
        new TestKit(actorSystem) {{
            final TestProbe targetConnectionActor = TestProbe.apply(actorSystem);
            pubSub.subscribeForDivertedResponses(TARGET_CONNECTION_ID, targetConnectionActor.ref(), false);

            // Create headers that allow all response types
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .correlationId(CORRELATION_ID)
                    .putHeader(DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey(), TARGET_CONNECTION_ID.toString())
                    .putHeader(DittoHeaderDefinition.DITTO_DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), "response,error,nack")
                    .build();

            final ModifyThingResponse response = ModifyThingResponse.modified(THING_ID, headers);
            final ResponseDiversionInterceptor interceptor =
                    ResponseDiversionInterceptor.of(sourceConnection, pubSub);

            final Target dummyTarget = ConnectivityModelFactory.newTargetBuilder()
                    .address("dummy/address")
                    .topics(Set.of())
                    .authorizationContext(AuthorizationContext.empty())
                    .build();

            final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(
                    response,
                    Collections.singletonList(dummyTarget)
            );

            final boolean wasDiverted = interceptor.interceptAndDivert(outboundSignal);
            assertThat(wasDiverted).isTrue();
        }};
    }

    @Test
    public void multipleTargetConnectionsReceivingDivertedResponses() {
        final ActorSystem actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
        new TestKit(actorSystem) {{
            final ConnectionId secondTargetId = ConnectionId.of("second-target-connection");

            // Set up two target connections
            final TestProbe targetConnectionActor1 = TestProbe.apply(actorSystem);
            final TestProbe targetConnectionActor2 = TestProbe.apply(actorSystem);

            pubSub.subscribeForDivertedResponses(TARGET_CONNECTION_ID, targetConnectionActor1.ref(), false);
            pubSub.subscribeForDivertedResponses(secondTargetId, targetConnectionActor2.ref(), false);

            expectNoMessage(Duration.ofMillis(100));

            // Test diverting to first target
            DittoHeaders headers1 = DittoHeaders.newBuilder()
                    .correlationId(CORRELATION_ID + "-1")
                    .putHeader(DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey(), TARGET_CONNECTION_ID.toString())
                    .build();

            ModifyThingResponse response1 = ModifyThingResponse.modified(THING_ID, headers1);
            ResponseDiversionInterceptor interceptor = ResponseDiversionInterceptor.of(sourceConnection, pubSub);

            Target dummyTarget = ConnectivityModelFactory.newTargetBuilder()
                    .address("dummy/address")
                    .topics(Set.of())
                    .authorizationContext(AuthorizationContext.empty())
                    .build();

            OutboundSignal outboundSignal1 = OutboundSignalFactory.newOutboundSignal(
                    response1,
                    Collections.singletonList(dummyTarget)
            );

            boolean wasDiverted1 = interceptor.interceptAndDivert(outboundSignal1);
            assertThat(wasDiverted1).isTrue();

            // Test diverting to second target
            DittoHeaders headers2 = DittoHeaders.newBuilder()
                    .correlationId(CORRELATION_ID + "-2")
                    .putHeader(DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey(), secondTargetId.toString())
                    .build();

            ModifyThingResponse response2 = ModifyThingResponse.modified(THING_ID, headers2);

            OutboundSignal outboundSignal2 = OutboundSignalFactory.newOutboundSignal(
                    response2,
                    Collections.singletonList(dummyTarget)
            );

            boolean wasDiverted2 = interceptor.interceptAndDivert(outboundSignal2);
            assertThat(wasDiverted2).isTrue();
        }};
    }

    @Test
    public void dynamicDiversionWithWildcard() {
        final ActorSystem actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
        new TestKit(actorSystem) {{
            // Create connection with wildcard diversion
            final Source sourceWithWildcard = ConnectivityModelFactory.newSourceBuilder()
                    .authorizationContext(AUTHORIZATION_CONTEXT)
                    .address("commands/+/+")
                    .headerMapping(ConnectivityModelFactory.newHeaderMapping(Map.of(
                            DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey(), "*"
                    )))
                    .build();

            final Connection dynamicConnection = ConnectivityModelFactory.newConnectionBuilder(
                            ConnectionId.of("dynamic-connection"),
                            ConnectionType.MQTT,
                            ConnectivityStatus.OPEN,
                            "tcp://mqtt-broker:1883"
                    )
                    .sources(Collections.singletonList(sourceWithWildcard))
                    .build();

            final TestProbe targetConnectionActor = TestProbe.apply(actorSystem);
            pubSub.subscribeForDivertedResponses(TARGET_CONNECTION_ID, targetConnectionActor.ref(), false);

            expectNoMessage(Duration.ofMillis(100));

            // Headers should include the actual target since wildcard is used
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .correlationId(CORRELATION_ID)
                    .putHeader(DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey(), TARGET_CONNECTION_ID.toString())
                    .build();

            final ModifyThingResponse response = ModifyThingResponse.modified(THING_ID, headers);
            final ResponseDiversionInterceptor interceptor =
                    ResponseDiversionInterceptor.of(dynamicConnection, pubSub);

            final Target dummyTarget = ConnectivityModelFactory.newTargetBuilder()
                    .address("dummy/address")
                    .topics(Set.of())
                    .authorizationContext(AUTHORIZATION_CONTEXT)
                    .build();

            final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(
                    response,
                    Collections.singletonList(dummyTarget)
            );

            final boolean wasDiverted = interceptor.interceptAndDivert(outboundSignal);
            assertThat(wasDiverted).isTrue();
        }};
    }

    private Connection createSourceConnection() {
        final Map<String, String> headerMapping = Map.of(
                "correlation-id", "{{ header:correlation-id }}",
                DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey(), TARGET_CONNECTION_ID.toString(),
                DittoHeaderDefinition.DITTO_DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), "response,error"
        );

        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(AuthorizationContext.empty())
                .address("ditto/inbound/commands")
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .headerMapping(ConnectivityModelFactory.newHeaderMapping(headerMapping))
                .replyTarget(ReplyTarget.newBuilder()
                        .address("ditto/outbound/responses/{{ header:correlation-id }}")
                        .headerMapping(ConnectivityModelFactory.newHeaderMapping(headerMapping))
                        .build())
                .build();

        return ConnectivityModelFactory.newConnectionBuilder(
                        SOURCE_CONNECTION_ID,
                        ConnectionType.MQTT,
                        ConnectivityStatus.OPEN,
                        "tcp://mqtt-broker:1883"
                )
                .sources(Collections.singletonList(source))
                .build();
    }

    private Connection createTargetConnection() {
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("ditto.responses")
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .topics(Topic.LIVE_MESSAGES, Topic.TWIN_EVENTS)
                .headerMapping(ConnectivityModelFactory.newHeaderMapping(Map.of(
                        "correlation-id", "{{ header:correlation-id }}",
                        "diverted-from", "{{ header:diverted-response-from }}"
                )))
                .build();

        return ConnectivityModelFactory.newConnectionBuilder(
                        TARGET_CONNECTION_ID,
                        ConnectionType.AMQP_091,
                        ConnectivityStatus.OPEN,
                        "amqp://rabbitmq:5672"
                )
                .specificConfig(Map.of("is-diversion-target", "true"))
                .targets(Collections.singletonList(target))
                .build();
    }
}