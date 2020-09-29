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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.PayloadMappingDefinition;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.connectivity.mapping.ConnectivityCachingSignalEnrichmentProvider;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor.PublishMappedMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;

import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Abstract class to setup the infrastructure to test MessageMappingProcessorActor.
 */
public abstract class AbstractMessageMappingProcessorActorTest {

    static final ThingId KNOWN_THING_ID = ThingId.of("my:thing");

    static final Connection CONNECTION = TestConstants.createConnectionWithAcknowledgements();
    static final ConnectionId CONNECTION_ID = CONNECTION.getId();

    static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    static final String FAULTY_MAPPER = FaultyMessageMapper.ALIAS;
    static final String ADD_HEADER_MAPPER = AddHeaderMessageMapper.ALIAS;
    static final String DUPLICATING_MAPPER = DuplicatingMessageMapper.ALIAS;
    static final AuthorizationContext AUTHORIZATION_CONTEXT_WITH_DUPLICATES =
            TestConstants.Authorization.withUnprefixedSubjects(AUTHORIZATION_CONTEXT);
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

    ActorSystem actorSystem;
    ProtocolAdapterProvider protocolAdapterProvider;
    TestProbe connectionActorProbe;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        protocolAdapterProvider = ProtocolAdapterProvider.load(TestConstants.PROTOCOL_CONFIG, actorSystem);
        connectionActorProbe = TestProbe.apply("connectionActor", actorSystem);
        MockProxyActor.create(actorSystem);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }


    @SafeVarargs
    static void expectPublishedMappedMessage(final PublishMappedMessage publishMappedMessage,
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
                        .withValue("ditto.connectivity.signal-enrichment.provider",
                                ConfigValueFactory.fromAnyRef(
                                        ConnectivityCachingSignalEnrichmentProvider.class.getCanonicalName())
                        )
        );
        MockProxyActor.create(actorSystem);
    }

    void testExternalMessageInDittoProtocolIsProcessed(
            @Nullable final EnforcementFilter<CharSequence> enforcement) {
        testExternalMessageInDittoProtocolIsProcessed(enforcement, null);
    }

    void testExternalMessageInDittoProtocolIsProcessed(
            @Nullable final EnforcementFilter<CharSequence> enforcement, @Nullable final String mapping) {
        testExternalMessageInDittoProtocolIsProcessed(enforcement, true, mapping, r -> {});
    }

    void testExternalMessageInDittoProtocolIsProcessed(
            @Nullable final EnforcementFilter<CharSequence> enforcement, final boolean expectSuccess,
            @Nullable final String mapping, final Consumer<ThingErrorResponse> verifyErrorResponse) {

        new TestKit(actorSystem) {{
            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(this);
            final ModifyAttribute modifyCommand = createModifyAttributeCommand();
            final PayloadMapping mappings = ConnectivityModelFactory.newPayloadMapping(mapping);
            final ExternalMessage externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(modifyCommand.getDittoHeaders())
                            .withText(ProtocolFactory
                                    .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyCommand))
                                    .toJsonString())
                            .withAuthorizationContext(AUTHORIZATION_CONTEXT)
                            .withEnforcement(enforcement)
                            .withPayloadMapping(mappings)
                            .withInternalHeaders(HEADERS_WITH_REPLY_INFORMATION)
                            .build();

            TestProbe collectorProbe = TestProbe.apply("collector", actorSystem);
            messageMappingProcessorActor.tell(externalMessage, collectorProbe.ref());

            if (expectSuccess) {
                final ModifyAttribute modifyAttribute = expectMsgClass(ModifyAttribute.class);
                assertThat(modifyAttribute.getType()).isEqualTo(ModifyAttribute.TYPE);
                assertThat(modifyAttribute.getDittoHeaders().getCorrelationId()).contains(
                        modifyCommand.getDittoHeaders().getCorrelationId().orElse(null));
                assertThat(modifyAttribute.getDittoHeaders().getAuthorizationContext())
                        .isEqualTo(AUTHORIZATION_CONTEXT_WITH_DUPLICATES);
                // thing ID is included in the header for error reporting
                assertThat(modifyAttribute.getDittoHeaders())
                        .extracting(headers -> headers.get(DittoHeaderDefinition.ENTITY_ID.getKey()))
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

                messageMappingProcessorActor.tell(commandResponse, getRef());
                final OutboundSignal.Mapped responseMessage =
                        expectMsgClass(PublishMappedMessage.class).getOutboundSignal().first();

                if (ADD_HEADER_MAPPER.equals(mapping)) {
                    final Map<String, String> headers = responseMessage.getExternalMessage().getHeaders();
                    assertThat(headers).contains(AddHeaderMessageMapper.OUTBOUND_HEADER);
                }
            } else {
                final OutboundSignal errorResponse = expectMsgClass(PublishMappedMessage.class).getOutboundSignal();
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
            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(this);
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
            messageMappingProcessorActor.tell(externalMessage, collectorProbe.ref());

            final T received = expectMsgClass(expectedMessageClass);
            verifyReceivedMessage.accept(received);
        }};
    }

    <T> void testMessageMapping(final String correlationId,
            final AuthorizationContext context,
            final Class<T> expectedMessageClass,
            final Consumer<T> verifyReceivedMessage) {

        new TestKit(actorSystem) {{
            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(this);
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
            messageMappingProcessorActor.tell(externalMessage, collectorProbe.ref());

            final T received = expectMsgClass(expectedMessageClass);
            verifyReceivedMessage.accept(received);
        }};
    }

    ActorRef createMessageMappingProcessorActor(final TestKit kit) {
        final Props props =
                MessageMappingProcessorActor.props(kit.getRef(), kit.getRef(), getMessageMappingProcessor(),
                        CONNECTION, connectionActorProbe.ref(), 99);
        return actorSystem.actorOf(props);
    }

    MessageMappingProcessor getMessageMappingProcessor() {
        final Map<String, MappingContext> mappingDefinitions = new HashMap<>();
        mappingDefinitions.put(FAULTY_MAPPER, FaultyMessageMapper.CONTEXT);
        mappingDefinitions.put(ADD_HEADER_MAPPER, AddHeaderMessageMapper.CONTEXT);
        mappingDefinitions.put(DUPLICATING_MAPPER, DuplicatingMessageMapper.CONTEXT);
        final PayloadMappingDefinition payloadMappingDefinition =
                ConnectivityModelFactory.newPayloadMappingDefinition(mappingDefinitions);
        final DittoDiagnosticLoggingAdapter logger = Mockito.mock(DittoDiagnosticLoggingAdapter.class);
        Mockito.when(logger.withCorrelationId(Mockito.any(DittoHeaders.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(CharSequence.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class)))
                .thenReturn(logger);
        return MessageMappingProcessor.of(CONNECTION_ID, CONNECTION.getConnectionType(), payloadMappingDefinition,
                actorSystem,
                TestConstants.CONNECTIVITY_CONFIG,
                protocolAdapterProvider, logger);
    }

    void setUpProxyActor(final ActorRef recipient) {
        final ActorSelection actorSelection = actorSystem.actorSelection("/user/connectivityRoot" +
                "/connectivityProxyActor");
        Patterns.ask(actorSelection, recipient, Duration.ofSeconds(10L))
                .toCompletableFuture()
                .join();
    }

    ExternalMessage toExternalMessage(final Signal<?> signal) {
        final AuthorizationContext context =
                AuthorizationModelFactory.newAuthContext(
                        DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationModelFactory.newAuthSubject("ditto:ditto"));
        final JsonifiableAdaptable adaptable = ProtocolFactory
                .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(signal));
        return ExternalMessageFactory.newExternalMessageBuilder(Map.of())
                .withTopicPath(adaptable.getTopicPath())
                .withText(adaptable.toJsonString())
                .withAuthorizationContext(context)
                .withInternalHeaders(signal.getDittoHeaders())
                .build();
    }

    static ModifyAttribute createModifyAttributeCommand() {
        final Map<String, String> headers = new HashMap<>();
        final String correlationId = UUID.randomUUID().toString();
        headers.put("correlation-id", correlationId);
        headers.put("content-type", "application/json");
        return ModifyAttribute.of(KNOWN_THING_ID, JsonPointer.of("foo"), JsonValue.of(42), DittoHeaders.of(headers));
    }

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
        public Optional<String> resolve(final String placeholderSource, final String name) {
            return Optional.of(placeholderSource);
        }

    }
}
