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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.disableLogging;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.EnforcementFactoryFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.EnforcementFilterFactory;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.PayloadMappingDefinition;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.mapping.ConnectivityCachingSignalEnrichmentProvider;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor.PublishMappedMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
 * Tests {@link MessageMappingProcessorActor}.
 */
public final class MessageMappingProcessorActorTest {

    private static final ThingId KNOWN_THING_ID = ThingId.of("my:thing");

    private static final Connection CONNECTION = TestConstants.createConnectionWithAcknowledgements();
    private static final ConnectionId CONNECTION_ID = CONNECTION.getId();

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final String FAULTY_MAPPER = FaultyMessageMapper.ALIAS;
    private static final String ADD_HEADER_MAPPER = AddHeaderMessageMapper.ALIAS;
    private static final String DUPLICATING_MAPPER = DuplicatingMessageMapper.ALIAS;
    private static final AuthorizationContext AUTHORIZATION_CONTEXT_WITH_DUPLICATES =
            TestConstants.Authorization.withUnprefixedSubjects(AUTHORIZATION_CONTEXT);
    private static final DittoHeaders headersWithReplyInformation = DittoHeaders.newBuilder()
            .replyTarget(0)
            .expectedResponseTypes(ResponseType.RESPONSE, ResponseType.ERROR, ResponseType.NACK)
            .build();

    private static final HeaderMapping CORRELATION_ID_AND_SOURCE_HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(JsonObject.newBuilder()
                    .set("correlation-id", "{{ header:correlation-id }}")
                    .set("source", "{{ request:subjectId }}")
                    .build());

    private static final HeaderMapping SOURCE_HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(JsonObject.newBuilder()
                    .set("source", "{{ request:subjectId }}")
                    .build());

    private ActorSystem actorSystem;
    private ProtocolAdapterProvider protocolAdapterProvider;
    private TestProbe connectionActorProbe;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        protocolAdapterProvider = ProtocolAdapterProvider.load(TestConstants.PROTOCOL_CONFIG, actorSystem);
        connectionActorProbe = TestProbe.apply("connectionActor", actorSystem);
        MockConciergeForwarderActor.create(actorSystem);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void testExternalMessageInDittoProtocolIsProcessedWithDefaultMapper() {
        testExternalMessageInDittoProtocolIsProcessed(null);
    }

    @Test
    public void testExternalMessageInDittoProtocolIsProcessedWithCustomMapper() {
        testExternalMessageInDittoProtocolIsProcessed(null, ADD_HEADER_MAPPER);
    }

    @Test
    public void testThingIdEnforcementExternalMessageInDittoProtocolIsProcessed() {
        final Enforcement mqttEnforcement =
                ConnectivityModelFactory.newEnforcement("{{ test:placeholder }}",
                        "mqtt/topic/{{ thing:namespace }}/{{ thing:name }}");
        final EnforcementFilterFactory<String, CharSequence> factory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(mqttEnforcement, new TestPlaceholder());
        final EnforcementFilter<CharSequence> enforcementFilter = factory.getFilter("mqtt/topic/my/thing");
        testExternalMessageInDittoProtocolIsProcessed(enforcementFilter);
    }

    @Test
    public void testThingIdEnforcementExternalMessageInDittoProtocolIsProcessedExpectErrorResponse() {
        disableLogging(actorSystem);
        final Enforcement mqttEnforcement =
                ConnectivityModelFactory.newEnforcement("{{ test:placeholder }}",
                        "mqtt/topic/{{ thing:namespace }}/{{ thing:name }}");
        final EnforcementFilterFactory<String, CharSequence> factory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(mqttEnforcement, new TestPlaceholder());
        final EnforcementFilter<CharSequence> enforcementFilter = factory.getFilter("some/invalid/target");
        testExternalMessageInDittoProtocolIsProcessed(enforcementFilter, false, null,
                r -> assertThat(r.getDittoRuntimeException())
                        .isInstanceOf(ConnectionSignalIdEnforcementFailedException.class)
        );
    }

    @Test
    public void testMappingFailedExpectErrorResponseWitMapperId() {
        disableLogging(actorSystem);
        testExternalMessageInDittoProtocolIsProcessed(null, false, FAULTY_MAPPER,
                r -> {
                    assertThat(r.getDittoRuntimeException()).isInstanceOf(MessageMappingFailedException.class);
                    assertThat(r.getDittoRuntimeException().getDescription())
                            .hasValueSatisfying(desc -> assertThat(desc).contains(FAULTY_MAPPER));
                }
        );
    }

    @Test
    public void testSignalEnrichment() {
        // GIVEN: test probe actor started with configured values
        final TestProbe conciergeForwarderProbe = TestProbe.apply("mockConciergeForwarderProbe", actorSystem);
        setUpConciergeForwarder(conciergeForwarderProbe.ref());

        new TestKit(actorSystem) {{
            // GIVEN: MessageMappingProcessor started with a test probe as the configured enrichment provider
            final ActorRef underTest = createMessageMappingProcessorActor(this);

            // WHEN: a signal is received with 2 targets, one with enrichment and one without
            final JsonFieldSelector extraFields = JsonFieldSelector.newInstance("attributes/x", "attributes/y");
            final AuthorizationSubject targetAuthSubject = AuthorizationSubject.newInstance("target:auth-subject");
            final AuthorizationSubject targetAuthSubjectWithoutIssuer =
                    AuthorizationSubject.newInstance("auth-subject");
            final Target targetWithEnrichment = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address")
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            targetAuthSubject))
                    .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                            .withExtraFields(extraFields)
                            .build())
                    .build();
            final Target targetWithoutEnrichment = ConnectivityModelFactory.newTargetBuilder(targetWithEnrichment)
                    .topics(Topic.TWIN_EVENTS)
                    .build();
            final Signal<?> signal = TestConstants.thingModified(Collections.emptyList());
            final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(signal,
                    Arrays.asList(targetWithEnrichment, targetWithoutEnrichment));
            underTest.tell(outboundSignal, getRef());

            // THEN: Receive a RetrieveThing command from the facade.
            final RetrieveThing retrieveThing = conciergeForwarderProbe.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(extraFields);
            assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext()).containsExactly(targetAuthSubject,
                    targetAuthSubjectWithoutIssuer);

            final JsonObject extra = JsonObject.newBuilder().set("/attributes/x", 5).build();
            conciergeForwarderProbe.reply(
                    RetrieveThingResponse.of(retrieveThing.getEntityId(), extra, retrieveThing.getDittoHeaders()));

            // THEN: a mapped signal without enrichment arrives first
            final PublishMappedMessage publishMappedMessage = expectMsgClass(PublishMappedMessage.class);
            int i = 0;
            expectPublishedMappedMessage(publishMappedMessage, i++, signal, targetWithoutEnrichment);

            // THEN: Receive an outbound signal with extra fields.
            expectPublishedMappedMessage(publishMappedMessage, i, signal, targetWithEnrichment,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra));
        }};
    }

    @SafeVarargs
    private static void expectPublishedMappedMessage(final PublishMappedMessage publishMappedMessage,
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

    @Test
    public void testSignalEnrichmentWithPayloadMappedTargets() {
        resetActorSystemWithCachingSignalEnrichmentProvider();
        final TestProbe conciergeForwarderProbe = TestProbe.apply("mockConciergeForwarder", actorSystem);
        setUpConciergeForwarder(conciergeForwarderProbe.ref());

        new TestKit(actorSystem) {{
            // GIVEN: MessageMappingProcessor started with a test probe as the configured enrichment provider
            final ActorRef underTest = createMessageMappingProcessorActor(this);

            // WHEN: a signal is received with 6 targets:
            //  - 1 with enrichment w/o payload mapping
            //  - 1 with enrichment with 1 payload mapping
            //  - 1 with enrichment with 2 payload mappings
            //  - 1 w/o enrichment w/o payload mapping
            //  - 1 w/o enrichment with 1 payload mapping
            //  - 1 w/o enrichment with 2 payload mappings
            final JsonFieldSelector extraFields = JsonFactory.newFieldSelector("attributes/x,attributes/y",
                    JsonParseOptions.newBuilder().withoutUrlDecoding().build());
            final AuthorizationSubject targetAuthSubject = AuthorizationSubject.newInstance("target:auth-subject");
            final AuthorizationSubject targetAuthSubjectWithoutIssuer =
                    AuthorizationSubject.newInstance("auth-subject");
            final Target targetWithEnrichment = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address")
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            targetAuthSubject))
                    .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                            .withExtraFields(extraFields)
                            .build())
                    .build();
            final Target targetWithEnrichmentAnd1PayloadMapper = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address/mapped/1")
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            targetAuthSubject))
                    .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                            .withExtraFields(extraFields)
                            .build())
                    .payloadMapping(ConnectivityModelFactory.newPayloadMapping(ADD_HEADER_MAPPER))
                    .build();
            final Target targetWithEnrichmentAnd2PayloadMappers = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address/mapped/2")
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            targetAuthSubject))
                    .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                            .withExtraFields(extraFields)
                            .build())
                    .payloadMapping(ConnectivityModelFactory.newPayloadMapping(DUPLICATING_MAPPER, ADD_HEADER_MAPPER))
                    .build();
            final Target targetWithoutEnrichment = ConnectivityModelFactory.newTargetBuilder(targetWithEnrichment)
                    .address("target/address/without/enrichment")
                    .topics(Topic.TWIN_EVENTS)
                    .build();
            final Target targetWithoutEnrichmentAnd1PayloadMapper =
                    ConnectivityModelFactory.newTargetBuilder(targetWithEnrichment)
                            .address("target/address/without/enrichment/with/1/payloadmapper")
                            .topics(Topic.TWIN_EVENTS)
                            .payloadMapping(ConnectivityModelFactory.newPayloadMapping(ADD_HEADER_MAPPER))
                            .build();
            final Target targetWithoutEnrichmentAnd2PayloadMappers =
                    ConnectivityModelFactory.newTargetBuilder(targetWithEnrichment)
                            .address("target/address/without/enrichment/with/2/payloadmappers")
                            .topics(Topic.TWIN_EVENTS)
                            .payloadMapping(
                                    ConnectivityModelFactory.newPayloadMapping(ADD_HEADER_MAPPER, DUPLICATING_MAPPER))
                            .build();
            final Signal<?> signal = TestConstants.thingModified(Collections.emptyList())
                    .setRevision(8L); // important to set revision to same value as cache lookup retrieves
            final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(signal, Arrays.asList(
                    targetWithEnrichment,
                    targetWithoutEnrichment,
                    targetWithEnrichmentAnd1PayloadMapper,
                    targetWithoutEnrichmentAnd1PayloadMapper,
                    targetWithEnrichmentAnd2PayloadMappers,
                    targetWithoutEnrichmentAnd2PayloadMappers)
            );
            underTest.tell(outboundSignal, getRef());
            // THEN: Receive a RetrieveThing command from the facade.
            final RetrieveThing retrieveThing = conciergeForwarderProbe.expectMsgClass(RetrieveThing.class);
            final JsonFieldSelector extraFieldsWithAdditionalCachingSelectedOnes = JsonFactory.newFieldSelectorBuilder()
                    .addPointers(extraFields)
                    .addFieldDefinition(Thing.JsonFields.REVISION) // additionally always select the revision
                    .build();
            assertThat(retrieveThing.getSelectedFields()).contains(extraFieldsWithAdditionalCachingSelectedOnes);
            assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext()).containsExactly(targetAuthSubject,
                    targetAuthSubjectWithoutIssuer);
            final JsonObject extra = JsonObject.newBuilder()
                    .set("/attributes/x", 5)
                    .build();
            final JsonObject extraForCachingFacade = JsonObject.newBuilder()
                    .set("_revision", 8)
                    .setAll(extra)
                    .build();
            conciergeForwarderProbe.reply(RetrieveThingResponse.of(retrieveThing.getEntityId(), extraForCachingFacade,
                    retrieveThing.getDittoHeaders()));

            // THEN: mapped messages arrive in a batch.
            final PublishMappedMessage publishMappedMessage = expectMsgClass(PublishMappedMessage.class);
            int i = 0;

            // THEN: the first mapped signal is without enrichment
            expectPublishedMappedMessage(publishMappedMessage, i++, signal, targetWithoutEnrichment,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );

            // THEN: the second mapped signal is without enrichment and applied 1 payload mapper arrives
            expectPublishedMappedMessage(publishMappedMessage, i++, signal,
                    targetWithoutEnrichmentAnd1PayloadMapper,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders())
                            .contains(AddHeaderMessageMapper.OUTBOUND_HEADER)
            );

            // THEN: a mapped signal without enrichment and applied 2 payload mappers arrives causing 3 messages
            //  as 1 mapper duplicates the message
            expectPublishedMappedMessage(publishMappedMessage, i++, signal,
                    targetWithoutEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders())
                            .contains(AddHeaderMessageMapper.OUTBOUND_HEADER)
            );
            expectPublishedMappedMessage(publishMappedMessage, i++, signal,
                    targetWithoutEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );
            expectPublishedMappedMessage(publishMappedMessage, i++, signal,
                    targetWithoutEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );


            // THEN: Receive an outbound signal with extra fields.
            expectPublishedMappedMessage(publishMappedMessage, i++, signal, targetWithEnrichment,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );

            // THEN: Receive an outbound signal with extra fields and with mapped payload.
            expectPublishedMappedMessage(publishMappedMessage, i++, signal,
                    targetWithEnrichmentAnd1PayloadMapper,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders())
                            .contains(AddHeaderMessageMapper.OUTBOUND_HEADER)
            );

            // THEN: a mapped signal with enrichment and applied 2 payload mappers arrives causing 3 messages
            //  as 1 mapper duplicates the message
            expectPublishedMappedMessage(publishMappedMessage, i++, signal,
                    targetWithEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );
            expectPublishedMappedMessage(publishMappedMessage, i++, signal,
                    targetWithEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );
            expectPublishedMappedMessage(publishMappedMessage, i, signal,
                    targetWithEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders())
                            .contains(AddHeaderMessageMapper.OUTBOUND_HEADER)
            );
        }};
    }

    private void resetActorSystemWithCachingSignalEnrichmentProvider() {
        TestKit.shutdownActorSystem(actorSystem);
        actorSystem = ActorSystem.create("AkkaTestSystemWithCachingSignalEnrichmentProvider",
                TestConstants.CONFIG
                        .withValue("ditto.connectivity.signal-enrichment.provider",
                                ConfigValueFactory.fromAnyRef(
                                        ConnectivityCachingSignalEnrichmentProvider.class.getCanonicalName())
                        )
        );
        MockConciergeForwarderActor.create(actorSystem);
    }

    private void testExternalMessageInDittoProtocolIsProcessed(
            @Nullable final EnforcementFilter<CharSequence> enforcement) {
        testExternalMessageInDittoProtocolIsProcessed(enforcement, null);
    }

    private void testExternalMessageInDittoProtocolIsProcessed(
            @Nullable final EnforcementFilter<CharSequence> enforcement, @Nullable final String mapping) {
        testExternalMessageInDittoProtocolIsProcessed(enforcement, true, mapping, r -> {});
    }

    private void testExternalMessageInDittoProtocolIsProcessed(
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
                            .withInternalHeaders(headersWithReplyInformation)
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

    @Test
    public void testReplacementOfPlaceholders() {
        final String correlationId = UUID.randomUUID().toString();
        final AuthorizationContext contextWithPlaceholders =
                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationModelFactory.newAuthSubject(
                                "integration:{{header:correlation-id}}:hub-{{   header:content-type   }}"),
                        AuthorizationModelFactory.newAuthSubject(
                                "integration:{{header:content-type}}:hub-{{ header:correlation-id }}"));

        final AuthorizationContext expectedAuthContext = TestConstants.Authorization.withUnprefixedSubjects(
                AuthorizationModelFactory.newAuthContext(
                        DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationModelFactory.newAuthSubject(
                                "integration:" + correlationId + ":hub-application/json"),
                        AuthorizationModelFactory.newAuthSubject("integration:application/json:hub-" + correlationId)));

        testMessageMapping(correlationId, contextWithPlaceholders, ModifyAttribute.class, modifyAttribute -> {
            assertThat(modifyAttribute.getType()).isEqualTo(ModifyAttribute.TYPE);
            assertThat(modifyAttribute.getDittoHeaders().getCorrelationId()).contains(correlationId);
            assertThat(modifyAttribute.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjects())
                    .isEqualTo(expectedAuthContext.getAuthorizationSubjects());

            // mapped by source <- {{ request:subjectId }}
            assertThat(modifyAttribute.getDittoHeaders().get("source"))
                    .contains("integration:" + correlationId + ":hub-application/json");
        });
    }

    @Test
    public void testHeadersOnTwinTopicPathCombinationError() {
        final String correlationId = UUID.randomUUID().toString();

        final AuthorizationContext authorizationContext = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationModelFactory.newAuthSubject("integration:" + correlationId + ":hub-application/json"));

        new TestKit(actorSystem) {{

            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(this);

            // WHEN: message sent valid topic and invalid topic+path combination
            final String messageContent = "{  \n" +
                    "   \"topic\":\"Testspace/octopus/things/twin/commands/retrieve\",\n" +
                    "   \"path\":\"/policyId\",\n" +
                    "   \"headers\":{  \n" +
                    "      \"correlation-id\":\"" + correlationId + "\"\n" +
                    "   }\n" +
                    "}";
            final ExternalMessage inboundMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withInternalHeaders(headersWithReplyInformation)
                            .withText(messageContent)
                            .withAuthorizationContext(authorizationContext)
                            .build();

            final TestProbe collectorProbe = TestProbe.apply("collector", actorSystem);
            messageMappingProcessorActor.tell(inboundMessage, collectorProbe.ref());

            // THEN: resulting error response retains the correlation ID
            final OutboundSignal outboundSignal =
                    expectMsgClass(PublishMappedMessage.class).getOutboundSignal();
            assertThat(outboundSignal)
                    .extracting(o -> o.getSource().getDittoHeaders().getCorrelationId()
                            .orElseThrow(() -> new AssertionError("correlation-id not found")))
                    .isEqualTo(correlationId);
        }};
    }

    @Test
    public void testMessageWithoutCorrelationId() {

        final AuthorizationContext connectionAuthContext = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationModelFactory.newAuthSubject("integration:application/json:hub"),
                AuthorizationModelFactory.newAuthSubject("integration:hub-application/json"));

        final AuthorizationContext expectedMessageAuthContext =
                TestConstants.Authorization.withUnprefixedSubjects(connectionAuthContext);

        testMessageMappingWithoutCorrelationId(connectionAuthContext, ModifyAttribute.class, modifyAttribute -> {
            assertThat(modifyAttribute.getType()).isEqualTo(ModifyAttribute.TYPE);
            assertThat(modifyAttribute.getDittoHeaders().getAuthorizationContext()).isEqualTo(
                    expectedMessageAuthContext);

        });
    }

    private <T> void testMessageMappingWithoutCorrelationId(
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

    @Test
    public void testTopicOnLiveTopicPathCombinationError() {
        final String correlationId = UUID.randomUUID().toString();

        final AuthorizationContext authorizationContext = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationModelFactory.newAuthSubject("integration:" + correlationId + ":hub-application/json"));

        new TestKit(actorSystem) {{

            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(this);

            // WHEN: message sent with valid topic and invalid topic+path combination
            final String topicPrefix = "Testspace/octopus/things/live/";
            final String topic = topicPrefix + "commands/retrieve";
            final String path = "/policyId";
            final String messageContent = "{  \n" +
                    "   \"topic\":\"" + topic + "\",\n" +
                    "   \"path\":\"" + path + "\"\n" +
                    "}";
            final ExternalMessage inboundMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withInternalHeaders(headersWithReplyInformation)
                            .withText(messageContent)
                            .withAuthorizationContext(authorizationContext)
                            .build();

            final TestProbe collectorProbe = TestProbe.apply("collector", actorSystem);
            messageMappingProcessorActor.tell(inboundMessage, collectorProbe.ref());

            // THEN: resulting error response retains the topic including thing ID and channel
            final ExternalMessage outboundMessage =
                    expectMsgClass(PublishMappedMessage.class).getOutboundSignal().first().getExternalMessage();
            assertThat(outboundMessage)
                    .extracting(e -> JsonFactory.newObject(e.getTextPayload().orElse("{}"))
                            .getValue("topic"))
                    .isEqualTo(Optional.of(JsonValue.of(topicPrefix + "errors")));
        }};
    }

    @Test
    public void testUnknownPlaceholdersExpectUnresolvedPlaceholderException() {
        disableLogging(actorSystem);

        final String placeholderKey = "header:unknown";
        final String placeholder = "{{" + placeholderKey + "}}";
        final AuthorizationContext contextWithUnknownPlaceholder = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationModelFactory.newAuthSubject("integration:" + placeholder));

        testMessageMapping(UUID.randomUUID().toString(), contextWithUnknownPlaceholder,
                PublishMappedMessage.class, error -> {
                    final OutboundSignal.Mapped outboundSignal = error.getOutboundSignal().first();
                    final UnresolvedPlaceholderException exception = UnresolvedPlaceholderException.fromMessage(
                            outboundSignal.getExternalMessage()
                                    .getTextPayload()
                                    .orElseThrow(() -> new IllegalArgumentException("payload was empty")),
                            DittoHeaders.of(outboundSignal.getExternalMessage().getHeaders()));
                    assertThat(exception.getMessage()).contains(placeholderKey);
                });
    }

    private <T> void testMessageMapping(final String correlationId,
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
                    .withInternalHeaders(headersWithReplyInformation)
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

    @Test
    public void testCommandResponseIsProcessed() {
        new TestKit(actorSystem) {{
            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(this);

            final String correlationId = UUID.randomUUID().toString();
            final ModifyAttributeResponse commandResponse =
                    ModifyAttributeResponse.modified(KNOWN_THING_ID, JsonPointer.of("foo"),
                            headersWithReplyInformation.toBuilder().correlationId(correlationId).build());

            messageMappingProcessorActor.tell(commandResponse, getRef());

            final OutboundSignal.Mapped outboundSignal =
                    expectMsgClass(PublishMappedMessage.class).getOutboundSignal().first();
            assertThat(outboundSignal.getSource().getDittoHeaders().getCorrelationId())
                    .contains(correlationId);
        }};
    }


    @Test
    public void testThingNotAccessibleExceptionRetainsTopic() {
        new TestKit(actorSystem) {{
            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(this);

            // WHEN: message mapping processor receives ThingNotAccessibleException with thing-id set from topic path
            final String correlationId = UUID.randomUUID().toString();
            final ThingNotAccessibleException thingNotAccessibleException =
                    ThingNotAccessibleException.newBuilder(KNOWN_THING_ID)
                            .dittoHeaders(headersWithReplyInformation.toBuilder()
                                    .correlationId(correlationId)
                                    .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), KNOWN_THING_ID)
                                    .build())
                            .build();

            messageMappingProcessorActor.tell(thingNotAccessibleException, getRef());

            final OutboundSignal.Mapped outboundSignal =
                    expectMsgClass(PublishMappedMessage.class).getOutboundSignal().first();

            // THEN: correlation ID is preserved
            assertThat(outboundSignal.getSource().getDittoHeaders().getCorrelationId())
                    .contains(correlationId);

            // THEN: topic-path contains thing ID
            assertThat(outboundSignal.getExternalMessage())
                    .extracting(e -> JsonFactory.newObject(e.getTextPayload().orElse("{}")).getValue("topic"))
                    .isEqualTo(Optional.of(JsonFactory.newValue("my/thing/things/twin/errors")));
        }};
    }

    @Test
    public void testCommandResponseWithResponseRequiredFalseIsNotProcessed() {
        new TestKit(actorSystem) {{
            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(this);

            final ModifyAttributeResponse commandResponse =
                    ModifyAttributeResponse.modified(KNOWN_THING_ID, JsonPointer.of("foo"),
                            DittoHeaders.newBuilder()
                                    .responseRequired(false)
                                    .build());

            messageMappingProcessorActor.tell(commandResponse, getRef());

            expectNoMessage();
        }};
    }

    @Test
    public void testAggregationOfAcknowledgements() {
        new TestKit(actorSystem) {{
            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(this);
            final AcknowledgementRequest signalAck =
                    AcknowledgementRequest.parseAcknowledgementRequest("my-custom-ack-3");
            Set<AcknowledgementRequest> validationSet = new HashSet<>(Collections.singletonList(signalAck));
            for (AcknowledgementLabel label : CONNECTION.getSources().get(0).getRequestedAcknowledgementLabels()) {
                validationSet.add(AcknowledgementRequest.of(label));
            }
            final Map<String, String> headers = new HashMap<>();
            headers.put("content-type", "application/json");
            final AuthorizationContext context =
                    AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationModelFactory.newAuthSubject("ditto:ditto"));
            final ModifyAttribute modifyCommand =
                    ModifyAttribute.of(TestConstants.Things.THING_ID, JsonPointer.of("/attribute1"),
                            JsonValue.of("attributeValue"), DittoHeaders.newBuilder().acknowledgementRequest(
                                    signalAck).build());
            final JsonifiableAdaptable adaptable = ProtocolFactory
                    .wrapAsJsonifiableAdaptable(
                            DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyCommand, TopicPath.Channel.TWIN));

            final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                    .withTopicPath(adaptable.getTopicPath())
                    .withText(adaptable.toJsonString())
                    .withAuthorizationContext(context)
                    .withHeaderMapping(SOURCE_HEADER_MAPPING)
                    .withSourceAddress(CONNECTION.getSources().get(0).getAddresses().iterator().next())
                    .withSource(CONNECTION.getSources().get(0))
                    .build();

            final TestProbe collectorProbe = TestProbe.apply("collector", actorSystem);
            messageMappingProcessorActor.tell(message, collectorProbe.ref());

            final ModifyAttribute modifyAttribute = expectMsgClass(ModifyAttribute.class);
            assertThat(modifyAttribute.getDittoHeaders().getAcknowledgementRequests()).isEqualTo(validationSet);
        }};
    }

    @Test
    public void forwardsSearchCommandsToConnectionActor() {
        new TestKit(actorSystem) {{
            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(this);
            final Map<String, String> headers = new HashMap<>();
            headers.put("content-type", "application/json");
            final AuthorizationContext context =
                    AuthorizationModelFactory.newAuthContext(AuthorizationModelFactory.newAuthSubject("ditto:ditto"));
            final CancelSubscription searchCommand =
                    CancelSubscription.of("sub-" + UUID.randomUUID(), DittoHeaders.empty());
            final JsonifiableAdaptable adaptable = ProtocolFactory
                    .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(searchCommand));
            final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                    .withTopicPath(adaptable.getTopicPath())
                    .withText(adaptable.toJsonString())
                    .withAuthorizationContext(context)
                    .withHeaderMapping(SOURCE_HEADER_MAPPING)
                    .build();

            messageMappingProcessorActor.tell(externalMessage, getRef());

            final CancelSubscription received = connectionActorProbe.expectMsgClass(CancelSubscription.class);
            assertThat(received.getSubscriptionId()).isEqualTo(searchCommand.getSubscriptionId());
        }};
    }

    private ActorRef createMessageMappingProcessorActor(final TestKit kit) {
        final Props props =
                MessageMappingProcessorActor.props(kit.getRef(), kit.getRef(), getMessageMappingProcessor(),
                        CONNECTION, connectionActorProbe.ref(), 99);
        return actorSystem.actorOf(props);
    }

    private MessageMappingProcessor getMessageMappingProcessor() {
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
        return MessageMappingProcessor.of(CONNECTION_ID, payloadMappingDefinition, actorSystem,
                TestConstants.CONNECTIVITY_CONFIG,
                protocolAdapterProvider, logger);
    }

    private void setUpConciergeForwarder(final ActorRef recipient) {
        final ActorSelection actorSelection = actorSystem.actorSelection("/user/connectivityRoot/conciergeForwarder");
        Patterns.ask(actorSelection, recipient, Duration.ofSeconds(10L))
                .toCompletableFuture()
                .join();
    }

    private static ModifyAttribute createModifyAttributeCommand() {
        final Map<String, String> headers = new HashMap<>();
        final String correlationId = UUID.randomUUID().toString();
        headers.put("correlation-id", correlationId);
        headers.put("content-type", "application/json");
        return ModifyAttribute.of(KNOWN_THING_ID, JsonPointer.of("foo"), JsonValue.of(42), DittoHeaders.of(headers));
    }

    private static final class TestPlaceholder implements Placeholder<String> {

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
