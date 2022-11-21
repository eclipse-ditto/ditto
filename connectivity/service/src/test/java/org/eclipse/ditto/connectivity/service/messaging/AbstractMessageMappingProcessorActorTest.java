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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.PayloadMappingDefinition;
import org.eclipse.ditto.connectivity.model.SourceBuilder;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.DefaultConnectivitySignalEnrichmentProvider;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.mockito.Mockito;

import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.scaladsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Abstract class to setup the infrastructure to test MessageMappingProcessorActor.
 */
public abstract class AbstractMessageMappingProcessorActorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    static final ThingId KNOWN_THING_ID = ThingId.of("my:thing");

    static final Connection CONNECTION = TestConstants.createConnectionWithAcknowledgements();
    static final ConnectionId CONNECTION_ID = CONNECTION.getId();

    static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    static final String FAULTY_MAPPER = FaultyMessageMapper.ALIAS;
    static final String ADD_HEADER_MAPPER = AddHeaderMessageMapper.ALIAS;
    static final String DUPLICATING_MAPPER = DuplicatingMessageMapper.ALIAS;
    static final DittoHeaders HEADERS_WITH_REPLY_INFORMATION = DittoHeaders.newBuilder()
            .replyTarget(0)
            .expectedResponseTypes(ResponseType.RESPONSE, ResponseType.ERROR, ResponseType.NACK)
            .build();

    static final HeaderMapping CORRELATION_ID_AND_SOURCE_HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(JsonObject.newBuilder()
                    .set("correlation-id", "{{ header:correlation-id }}")
                    .set("source", "{{ request:subjectId }}")
                    .build());

    static final HeaderMapping SOURCE_HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(JsonObject.newBuilder()
                    .set("source", "{{ request:subjectId }}")
                    .set("qos", "{{ header:qos }}")
                    .build());

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    ActorSystem actorSystem;
    ProtocolAdapterProvider protocolAdapterProvider;
    TestProbe connectionActorProbe;
    ActorRef proxyActor;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        protocolAdapterProvider = ProtocolAdapterProvider.load(TestConstants.PROTOCOL_CONFIG, actorSystem);
        connectionActorProbe = TestProbe.apply("connectionActor", actorSystem);
        MockCommandForwarder.create(actorSystem, connectionActorProbe.ref());
        proxyActor = connectionActorProbe.expectMsgClass(ActorRef.class);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }


    @SafeVarargs
    static void expectPublishedMappedMessage(final BaseClientActor.PublishMappedMessage publishMappedMessage,
            final int index,
            final Signal<?> signal,
            final Target target,
            final Consumer<OutboundSignal.Mapped>... otherAssertionConsumers) {

        final OutboundSignal.Mapped mapped =
                publishMappedMessage.getOutboundSignal().getMappedOutboundSignals().get(index);
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(mapped).satisfies(outboundSignal -> {
                softly.assertThat(outboundSignal.getSource()).as("source is expected").isEqualTo(signal);
                softly.assertThat(outboundSignal.getTargets()).as("targets are expected").containsExactly(target);
            });
        }
        Arrays.asList(otherAssertionConsumers).forEach(con -> con.accept(mapped));
    }

    void resetActorSystemWithCachingSignalEnrichmentProvider() {
        TestKit.shutdownActorSystem(actorSystem);
        actorSystem = ActorSystem.create("AkkaTestSystemWithCachingSignalEnrichmentProvider",
                TestConstants.CONFIG
                        .withValue("ditto.extensions.signal-enrichment-provider.extension-class",
                                ConfigValueFactory.fromAnyRef(DefaultConnectivitySignalEnrichmentProvider.class.getCanonicalName()))
                        .withValue("ditto.extensions.signal-enrichment-provider.extension-config.cache.enabled",
                                ConfigValueFactory.fromAnyRef(true))
        );
        final TestProbe probe = TestProbe.apply(actorSystem);
        MockCommandForwarder.create(actorSystem, probe.ref());
        proxyActor = probe.expectMsgClass(ActorRef.class);
    }

    void testExternalMessageInDittoProtocolIsProcessed(
            @Nullable final EnforcementFilter<Signal<?>> enforcement) {
        testExternalMessageInDittoProtocolIsProcessed(enforcement, null);
    }

    void testExternalMessageInDittoProtocolIsProcessed(
            @Nullable final EnforcementFilter<Signal<?>> enforcement, @Nullable final String mapping) {
        testExternalMessageInDittoProtocolIsProcessed(enforcement, true, mapping, r -> {});
    }

    void testExternalMessageInDittoProtocolIsProcessed(
            @Nullable final EnforcementFilter<Signal<?>> enforcement, final boolean expectSuccess,
            @Nullable final String mapping, final Consumer<ThingErrorResponse> verifyErrorResponse) {

        new TestKit(actorSystem) {{
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);
            final ModifyAttribute modifyCommand = createModifyAttributeCommand();
            final PayloadMapping mappings = ConnectivityModelFactory.newPayloadMapping(mapping);
            final ExternalMessage externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(modifyCommand.getDittoHeaders())
                            .withText(ProtocolFactory
                                    .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyCommand))
                                    .toJsonString())
                            .withAuthorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                            .withEnforcement(enforcement)
                            .withPayloadMapping(mappings)
                            .withInternalHeaders(HEADERS_WITH_REPLY_INFORMATION)
                            .build();

            TestProbe collectorProbe = TestProbe.apply("collector", actorSystem);
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(externalMessage, collectorProbe.ref()),
                    ActorRef.noSender());

            if (expectSuccess) {
                final ModifyAttribute modifyAttribute = fishForMsg(this, ModifyAttribute.class);
                assertThat(modifyAttribute.getType()).isEqualTo(ModifyAttribute.TYPE);
                assertThat(modifyAttribute.getDittoHeaders().getCorrelationId()).contains(
                        modifyCommand.getDittoHeaders().getCorrelationId().orElse(null));
                assertThat(modifyAttribute.getDittoHeaders().getAuthorizationContext())
                        .isEqualTo(TestConstants.Authorization.AUTHORIZATION_CONTEXT);
                // thing ID is included in the header for error reporting
                assertThat(modifyAttribute.getDittoHeaders())
                        .extracting(headers -> {
                            final String prefixedEntityId = headers.get(DittoHeaderDefinition.ENTITY_ID.getKey());
                            return prefixedEntityId.substring(prefixedEntityId.indexOf(":") + 1);
                        })
                        .isEqualTo(KNOWN_THING_ID.toString());
                // internal headers added by consumer actors are appended
                assertThat(modifyAttribute.getDittoHeaders()).containsEntry("ditto-reply-target", "0");

                final String expectedMapperHeader = mapping == null ? "default" : mapping;
                assertThat(modifyAttribute.getDittoHeaders().getInboundPayloadMapper()).contains(expectedMapperHeader);

                if (ADD_HEADER_MAPPER.equals(mapping)) {
                    assertThat(modifyAttribute.getDittoHeaders()).contains(AddHeaderMessageMapper.INBOUND_HEADER);
                }

                final ModifyAttributeResponse commandResponse =
                        ModifyAttributeResponse.modified(KNOWN_THING_ID, modifyAttribute.getAttributePointer(),
                                modifyAttribute.getDittoHeaders());

                outboundMappingProcessorActor.tell(commandResponse, getRef());
                final OutboundSignal.Mapped responseMessage =
                        expectMsgClass(BaseClientActor.PublishMappedMessage.class).getOutboundSignal().first();

                if (ADD_HEADER_MAPPER.equals(mapping)) {
                    final Map<String, String> headers = responseMessage.getExternalMessage().getHeaders();
                    assertThat(headers).contains(AddHeaderMessageMapper.OUTBOUND_HEADER);
                }
            } else {
                final OutboundSignal errorResponse =
                        expectMsgClass(BaseClientActor.PublishMappedMessage.class).getOutboundSignal();
                assertThat(errorResponse.getSource()).isInstanceOf(ThingErrorResponse.class);
                final ThingErrorResponse response = (ThingErrorResponse) errorResponse.getSource();
                verifyErrorResponse.accept(response);
            }
        }};
    }

    <T> void testMessageMappingWithoutCorrelationId(
            final AuthorizationContext context,
            final Class<T> expectedMessageClass,
            final Consumer<T> verifyReceivedMessage) {

        new TestKit(actorSystem) {{
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);
            final Map<String, String> headers = new HashMap<>();
            headers.put("content-type", "application/json");
            final ModifyAttribute modifyCommand = ModifyAttribute.of(KNOWN_THING_ID, JsonPointer.of("foo"),
                    JsonValue.of(42), DittoHeaders.empty());
            final JsonifiableAdaptable adaptable = ProtocolFactory
                    .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyCommand));
            final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                    .withTopicPath(adaptable.getTopicPath())
                    .withText(adaptable.toJsonString())
                    .withAuthorizationContext(context)
                    .withHeaderMapping(SOURCE_HEADER_MAPPING)
                    .build();

            final TestProbe collectorProbe = TestProbe.apply("collector", actorSystem);
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(externalMessage, collectorProbe.ref()),
                    ActorRef.noSender());

            final T received = fishForMsg(this, expectedMessageClass);
            verifyReceivedMessage.accept(received);
        }};
    }

    <T> void testMessageMapping(final String correlationId,
            final AuthorizationContext context,
            final Class<T> expectedMessageClass,
            final Consumer<T> verifyReceivedMessage) {

        new TestKit(actorSystem) {{
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);
            final Map<String, String> headers = new HashMap<>();
            headers.put("correlation-id", correlationId);
            headers.put("content-type", "application/json");
            final ModifyAttribute modifyCommand = ModifyAttribute.of(KNOWN_THING_ID, JsonPointer.of("foo"),
                    JsonValue.of(42), DittoHeaders.empty());
            final JsonifiableAdaptable adaptable = ProtocolFactory
                    .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyCommand));
            final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                    .withInternalHeaders(HEADERS_WITH_REPLY_INFORMATION)
                    .withTopicPath(adaptable.getTopicPath())
                    .withText(adaptable.toJsonString())
                    .withAuthorizationContext(context)
                    .withHeaderMapping(CORRELATION_ID_AND_SOURCE_HEADER_MAPPING)
                    .build();

            final TestProbe collectorProbe = TestProbe.apply("collector", actorSystem);
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(externalMessage, collectorProbe.ref()),
                    ActorRef.noSender());

            final T received = fishForMsg(this, expectedMessageClass);
            verifyReceivedMessage.accept(received);
        }};
    }

    ActorRef createInboundMappingProcessorActor(final TestKit kit, final ActorRef outboundMappingProcessorActor) {
        return createInboundMappingProcessorActor(kit.getRef(), outboundMappingProcessorActor, kit);
    }

    ActorRef createInboundMappingProcessorActor(final ActorRef proxyActor,
            final ActorRef outboundMappingProcessorActor,
            final TestKit testKit) {

        final Map<String, MappingContext> mappingDefinitions = new HashMap<>();
        mappingDefinitions.put(FAULTY_MAPPER, FaultyMessageMapper.CONTEXT);
        mappingDefinitions.put(ADD_HEADER_MAPPER, AddHeaderMessageMapper.CONTEXT);
        mappingDefinitions.put(DUPLICATING_MAPPER, DuplicatingMessageMapper.CONTEXT);
        final var payloadMappingDefinition = ConnectivityModelFactory.newPayloadMappingDefinition(mappingDefinitions);
        final var logger = Mockito.mock(ThreadSafeDittoLoggingAdapter.class);
        Mockito.when(logger.withCorrelationId(Mockito.any(DittoHeaders.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class)))
                .thenReturn(logger);
        Mockito.when(logger.withMdcEntry(Mockito.any(CharSequence.class), Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        final var protocolAdapter = protocolAdapterProvider.getProtocolAdapter(null);
        final var inboundMappingProcessor = InboundMappingProcessor.of(
                CONNECTION.toBuilder().payloadMappingDefinition(payloadMappingDefinition).build(),
                TestConstants.CONNECTIVITY_CONFIG,
                actorSystem,
                protocolAdapter,
                logger
        );
        final var config = actorSystem.settings().config();
        final var inboundDispatchingSink = InboundDispatchingSink.createSink(CONNECTION,
                protocolAdapter.headerTranslator(),
                ActorSelection.apply(proxyActor, ""),
                connectionActorProbe.ref(),
                outboundMappingProcessorActor,
                testKit.getRef(),
                actorSystem,
                ConnectivityConfig.of(config),
                DittoHeadersValidator.get(actorSystem, ScopedConfig.dittoExtension(config)),
                null);

        final var inboundMappingSink = InboundMappingSink.createSink(
                List.of(inboundMappingProcessor),
                CONNECTION_ID,
                99,
                inboundDispatchingSink,
                TestConstants.CONNECTIVITY_CONFIG.getMappingConfig(),
                null,
                actorSystem.dispatchers().defaultGlobalDispatcher());

        return Source.actorRef(99, OverflowStrategy.dropNew())
                .to(inboundMappingSink)
                .run(Materializer.createMaterializer(actorSystem));
    }

    ActorRef createOutboundMappingProcessorActor(final TestKit kit) {
        final Map<String, MappingContext> mappingDefinitions = new HashMap<>();
        mappingDefinitions.put(FAULTY_MAPPER, FaultyMessageMapper.CONTEXT);
        mappingDefinitions.put(ADD_HEADER_MAPPER, AddHeaderMessageMapper.CONTEXT);
        mappingDefinitions.put(DUPLICATING_MAPPER, DuplicatingMessageMapper.CONTEXT);
        final PayloadMappingDefinition payloadMappingDefinition =
                ConnectivityModelFactory.newPayloadMappingDefinition(mappingDefinitions);
        final ThreadSafeDittoLoggingAdapter logger = mockLoggingAdapter();
        final ProtocolAdapter protocolAdapter = protocolAdapterProvider.getProtocolAdapter(null);
        final OutboundMappingProcessor outboundMappingProcessor = OutboundMappingProcessor.of(CONNECTION,
                TestConstants.CONNECTIVITY_CONFIG,
                actorSystem,
                protocolAdapter,
                logger);

        final Props props = OutboundMappingProcessorActor.props(kit.getRef(), List.of(outboundMappingProcessor),
                CONNECTION, TestConstants.CONNECTIVITY_CONFIG, 99);
        return actorSystem.actorOf(props);
    }

    void setUpProxyActor(final ActorRef recipient) {
        Patterns.ask(proxyActor, recipient, Duration.ofSeconds(30L)).toCompletableFuture().join();
    }

    ExternalMessage toExternalMessage(final Signal<?> signal, Consumer<SourceBuilder<?>> sourceModifier) {
        final var context = AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationModelFactory.newAuthSubject("ditto:ditto"));
        final JsonifiableAdaptable adaptable = ProtocolFactory
                .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(signal));
        final SourceBuilder<?> sourceBuilder = ConnectivityModelFactory.newSourceBuilder()
                .address("address")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT);
        sourceModifier.accept(sourceBuilder);
        return ExternalMessageFactory.newExternalMessageBuilder(Map.of())
                .withTopicPath(adaptable.getTopicPath())
                .withText(adaptable.toJsonString())
                .withAuthorizationContext(context)
                .withInternalHeaders(signal.getDittoHeaders())
                .withSource(sourceBuilder.build())
                .build();
    }

    ExternalMessage toExternalMessage(final Signal<?> signal) {
        return toExternalMessage(signal, sourceBuilder -> {
            if (signal instanceof Acknowledgement) {
                sourceBuilder.declaredAcknowledgementLabels(Set.of(((Acknowledgement) signal).getLabel()));
            } else if (signal instanceof Acknowledgements) {
                sourceBuilder.declaredAcknowledgementLabels(((Acknowledgements) signal).stream()
                        .map(Acknowledgement::getLabel)
                        .collect(Collectors.toSet())
                );
            }
        });
    }

    private ModifyAttribute createModifyAttributeCommand() {
        return ModifyAttribute.of(KNOWN_THING_ID,
                JsonPointer.of("foo"),
                JsonValue.of(42),
                DittoHeaders.newBuilder()
                        .correlationId(testNameCorrelationId.getCorrelationId())
                        .contentType(ContentType.APPLICATION_JSON)
                        .build());
    }

    static <T> T fishForMsg(final TestKit testKit, final Class<T> clazz) {
        return clazz.cast(testKit.fishForMessage(Duration.ofSeconds(3L), clazz.getSimpleName(), clazz::isInstance));
    }

    static ThreadSafeDittoLoggingAdapter mockLoggingAdapter() {
        final ThreadSafeDittoLoggingAdapter logger = Mockito.mock(ThreadSafeDittoLoggingAdapter.class);
        Mockito.when(logger.withMdcEntry(Mockito.any(CharSequence.class), Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(DittoHeaders.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class)))
                .thenReturn(logger);
        return logger;
    }

    @AllParametersAndReturnValuesAreNonnullByDefault
    static final class TestPlaceholder implements Placeholder<String> {

        @Override
        public String getPrefix() {
            return "test";
        }

        @Override
        public List<String> getSupportedNames() {
            return Collections.emptyList();
        }

        @Override
        public boolean supports(final String name) {
            return true;
        }

        @Override
        public List<String> resolveValues(final String placeholderSource, final String name) {
            return Collections.singletonList(placeholderSource);
        }

    }

}
