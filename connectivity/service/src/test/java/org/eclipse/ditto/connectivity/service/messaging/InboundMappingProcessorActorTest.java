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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.MappedInboundExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.service.mapping.DittoMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperRegistry;
import org.eclipse.ditto.connectivity.service.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Sink;
import akka.stream.scaladsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public final class InboundMappingProcessorActorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance(TestConstants.CONFIG);

    @Test
    public void onError() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // GIVEN: InboundMappingProcessorActor is constructed with a processor that throws an exception always.
            final TestProbe inboundDispatcher = actorSystemResource.newTestProbe("inboundDispatcher");
            final Sink<Object, ?> inboundSink =
                    Sink.foreach(x -> inboundDispatcher.ref().tell(x, ActorRef.noSender()));
            final InboundMappingProcessor throwingProcessor = createThrowingProcessor();
            final Sink<Object, NotUsed> inboundMappingSink = InboundMappingSink.createSink(List.of(throwingProcessor),
                    TestConstants.createRandomConnectionId(),
                    1,
                    inboundSink,
                    TestConstants.MAPPING_CONFIG,
                    null,
                    getDefaultGlobalDispatcher());
            final ActorRef underTest = Source.actorRef(1, OverflowStrategy.dropNew())
                    .to(inboundMappingSink)
                    .run(actorSystemResource.getMaterializer());

            // WHEN: InboundMappingProcessorActor receives a text message.
            final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(Map.of())
                    .withSource(TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT.get(0))
                    .withText("text")
                    // attach non-null payload mapping to avoid using the default mapper
                    .withPayloadMapping(Mockito.mock(PayloadMapping.class))
                    .build();
            underTest.tell(new ExternalMessageWithSender(message, getRef()), ActorRef.noSender());

            // THEN: InboundDispatchingActor receives 1 error outcome with the exception thrown.
            final InboundMappingOutcomes outcomes =
                    inboundDispatcher.expectMsgClass(InboundMappingOutcomes.class);
            assertThat(outcomes.getOutcomes()).hasSize(1);
            outcomes.getOutcomes().get(0).accept(new MappingOutcome.Visitor<>() {
                @Override
                public Integer onMapped(final String mapperId, final MappedInboundExternalMessage mapped) {
                    throw new AssertionError("Expect error, got: mapped " + mapped);
                }

                @Override
                public Integer onDropped(final String mapperId,
                        @Nullable final ExternalMessage droppedMessage) {
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
        Mockito.doAnswer(inv -> new DittoMessageMapper(Mockito.mock(ActorSystem.class), Mockito.mock(Config.class)))
                .when(registry).getDefaultMapper();
        Mockito.doAnswer(inv -> List.of(new ThrowingMapper())).when(registry).getMappers(Mockito.any());
        final ThreadSafeDittoLoggingAdapter logger = Mockito.mock(ThreadSafeDittoLoggingAdapter.class);
        Mockito.doAnswer(inv -> logger).when(logger).withCorrelationId(Mockito.<CharSequence>any());
        Mockito.doAnswer(inv -> logger).when(logger).withCorrelationId(Mockito.<DittoHeaders>any());
        final Connection connection =
                TestConstants.createConnection(ConnectionId.of("connectionId"), ConnectionType.MQTT);
        return InboundMappingProcessor.of(connection,
                registry,
                logger,
                Mockito.mock(ProtocolAdapter.class),
                Mockito.mock(DittoHeadersValidator.class));
    }

    private MessageDispatcher getDefaultGlobalDispatcher() {
        final var actorSystem = actorSystemResource.getActorSystem();
        final var dispatchers = actorSystem.dispatchers();
        return dispatchers.defaultGlobalDispatcher();
    }

}
