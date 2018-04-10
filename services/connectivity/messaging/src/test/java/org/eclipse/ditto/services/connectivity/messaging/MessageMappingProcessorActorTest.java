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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link MessageMappingProcessorActor}.
 */
public class MessageMappingProcessorActorTest {

    private static final String CONNECTION_ID = "testConnection";
    private static final AuthorizationContext AUTHORIZATION_CONTEXT =
            AuthorizationContext.newInstance(AuthorizationSubject.newInstance("foo:bar"));

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private static ActorSystem actorSystem;
    private static ActorRef pubSubMediator;


    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void testExternalMessageInDittoProtocolIsProcessed() {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            pubSubMediator.tell(new DistributedPubSubMediator.Put(getTestActor()), null);

            final ActorRef messageMappingProcessorActor =
                    createMessageMappingProcessorActor(pubSubTargetPath, getRef());

            final Map<String, String> headers = new HashMap<>();
            final String correlationId = UUID.randomUUID().toString();
            headers.put("correlation-id", correlationId);
            headers.put("content-type", "application/json");
            final ModifyAttribute modifyCommand = ModifyAttribute.of("my:thing", JsonPointer.of("foo"),
                    JsonValue.of(42), DittoHeaders.empty());
            final ExternalMessage externalMessage = ConnectivityModelFactory.newExternalMessageBuilder(headers)
                    .withText(ProtocolFactory
                            .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyCommand))
                            .toJsonString())
                    .build();

            messageMappingProcessorActor.tell(externalMessage, getRef());

            final ModifyAttribute modifyAttribute = expectMsgClass(ModifyAttribute.class);
            assertThat(modifyAttribute.getType()).isEqualTo(ModifyAttribute.TYPE);
            assertThat(modifyAttribute.getDittoHeaders().getCorrelationId()).contains(correlationId);
        }};
    }

    @Test
    public void testCommandResponseIsProcessed() {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            pubSubMediator.tell(new DistributedPubSubMediator.Put(getTestActor()), null);

            final ActorRef messageMappingProcessorActor =
                    createMessageMappingProcessorActor(pubSubTargetPath, getRef());

            final String correlationId = UUID.randomUUID().toString();
            final ModifyAttributeResponse commandResponse =
                    ModifyAttributeResponse.modified("my:thing", JsonPointer.of("foo"),
                            DittoHeaders.newBuilder()
                                    .correlationId(correlationId)
                                    .build());

            messageMappingProcessorActor.tell(commandResponse, getRef());

            final ExternalMessage externalMessage = expectMsgClass(ExternalMessage.class);
            assertThat(externalMessage.findContentType()).contains(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
            assertThat(externalMessage.getHeaders().get("correlation-id")).contains(correlationId);
        }};
    }

    @Test
    public void testCommandResponseWithResponseRequiredFalseIsNotProcessed() {
        new TestKit(actorSystem) {
            {
                final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
                pubSubMediator.tell(new DistributedPubSubMediator.Put(getTestActor()), null);

                final ActorRef messageMappingProcessorActor =
                        createMessageMappingProcessorActor(pubSubTargetPath, getRef());

                final ModifyAttributeResponse commandResponse =
                        ModifyAttributeResponse.modified("my:thing", JsonPointer.of("foo"),
                                DittoHeaders.newBuilder()
                                        .responseRequired(false)
                                        .build());

                messageMappingProcessorActor.tell(commandResponse, getRef());

                expectNoMessage();
            }
        };
    }

    private ActorRef createMessageMappingProcessorActor(final String pubSubTargetPath, final ActorRef ref) {
        final Props props = MessageMappingProcessorActor.props(pubSubMediator,
                pubSubTargetPath,
                ref,
                AUTHORIZATION_CONTEXT,
                getMessageMappingProcessor(null),
                CONNECTION_ID);
        return actorSystem.actorOf(props);
    }

    private MessageMappingProcessor getMessageMappingProcessor(@Nullable final MappingContext mappingContext) {
        return MessageMappingProcessor.of(CONNECTION_ID, mappingContext, ((ExtendedActorSystem) actorSystem).dynamicAccess(),
                Mockito.mock(DiagnosticLoggingAdapter.class));
    }

}
