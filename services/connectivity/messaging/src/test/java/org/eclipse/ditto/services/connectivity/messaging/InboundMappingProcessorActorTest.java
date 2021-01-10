/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperRegistry;
import org.eclipse.ditto.services.connectivity.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public final class InboundMappingProcessorActorTest {

    private ActorSystem system = null;

    @Before
    public void init() {
        system = ActorSystem.create(getClass().getSimpleName(), TestConstants.CONFIG);
    }

    @After
    public void cleanup() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void onError() {
        new TestKit(system) {{
            // GIVEN: InboundMappingProcessorActor is constructed with a processor that throws an exception always.
            final TestProbe inboundDispatcher = TestProbe.apply("inboundDispatcher", system);
            final InboundMappingProcessor throwingProcessor = createThrowingProcessor();
            final Props props = InboundMappingProcessorActor.props(throwingProcessor, HeaderTranslator.empty(),
                    TestConstants.createConnection(), 1, inboundDispatcher.ref());
            final ActorRef underTest = system.actorOf(props);

            // WHEN: InboundMappingProcessorActor receives a text message.
            final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(Map.of())
                    .withSource(TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT.get(0))
                    .withText("text")
                    // attach non-null payload mapping to avoid using the default mapper
                    .withPayloadMapping(Mockito.mock(PayloadMapping.class))
                    .build();
            underTest.tell(message, getRef());

            // THEN: InboundDispatchingActor receives 1 error outcome with the exception thrown.
            final InboundMappingOutcomes outcomes = inboundDispatcher.expectMsgClass(InboundMappingOutcomes.class);
            assertThat(outcomes.getOutcomes()).hasSize(1);
            outcomes.getOutcomes().get(0).accept(new MappingOutcome.Visitor<>() {
                @Override
                public Integer onMapped(final String mapperId, final MappedInboundExternalMessage mapped) {
                    throw new AssertionError("Expect error, got: mapped " + mapped);
                }

                @Override
                public Integer onDropped(final String mapperId, @Nullable final ExternalMessage droppedMessage) {
                    throw new AssertionError("Expect error, got: dropped " + droppedMessage);
                }

                @Override
                public Integer onError(final String mapperId, final Exception error,
                        @Nullable final TopicPath topicPath,
                        @Nullable final ExternalMessage externalMessage) {
                    assertThat(error).isEqualTo(ThrowingMapper.EXCEPTION);
                    return 0;
                }
            });
        }};
    }

    private static InboundMappingProcessor createThrowingProcessor() {
        final MessageMapperRegistry registry = Mockito.mock(MessageMapperRegistry.class);
        Mockito.doAnswer(inv -> new DittoMessageMapper()).when(registry).getDefaultMapper();
        Mockito.doAnswer(inv -> List.of(new ThrowingMapper())).when(registry).getMappers(Mockito.any());
        final ThreadSafeDittoLoggingAdapter logger = Mockito.mock(ThreadSafeDittoLoggingAdapter.class);
        Mockito.doAnswer(inv -> logger).when(logger).withCorrelationId(Mockito.<CharSequence>any());
        Mockito.doAnswer(inv -> logger).when(logger).withCorrelationId(Mockito.<DittoHeaders>any());
        return InboundMappingProcessor.forTest(ConnectionId.of("connectionId"),
                ConnectionType.MQTT,
                registry,
                logger,
                Mockito.mock(ProtocolAdapter.class),
                Mockito.mock(DittoHeadersSizeChecker.class));
    }

}
