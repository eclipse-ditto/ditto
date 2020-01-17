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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
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
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.connectivity.mapping.ConnectivitySignalEnrichmentProvider;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor.PublishMappedMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.models.signalenrichment.ByRoundTripSignalEnrichmentFacade;
import org.eclipse.ditto.services.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.eclipse.ditto.services.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.testkit.TestKit$;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link MessageMappingProcessorActor}.
 */
public final class MessageMappingProcessorActorTest {

    private static final ThingId KNOWN_THING_ID = ThingId.of("my:thing");

    private static final ConnectionId CONNECTION_ID = ConnectionId.of("testConnection");

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final String FAULTY_MAPPER = "faulty";
    private static final String ADD_HEADER_MAPPER = "header";
    private static final String DUPLICATING_MAPPER = "duplicating";

    private static final HeaderMapping CORRELATION_ID_AND_SOURCE_HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(JsonObject.newBuilder()
                    .set("correlation-id", "{{ header:correlation-id }}")
                    .set("source", "{{ request:subjectId }}")
                    .build());

    private ActorSystem actorSystem;
    private ProtocolAdapterProvider protocolAdapterProvider;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        protocolAdapterProvider = ProtocolAdapterProvider.load(TestConstants.PROTOCOL_CONFIG, actorSystem);
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
        // Reset test kit test actor ID to ensure name of test probe matches configuration
        TestKit$.MODULE$.testActorId().set(0);
        final String userGuardianPrefix = "/system/";
        final String testProbeSuffix = "-1";
        final String commandHandlerPath = TestConstants.MAPPING_CONFIG.getSignalEnrichmentProviderPath();
        assertThat(commandHandlerPath).startsWith(userGuardianPrefix).endsWith(testProbeSuffix);
        final String commandHandlerName = commandHandlerPath.substring(userGuardianPrefix.length(),
                commandHandlerPath.length() - testProbeSuffix.length());
        final TestProbe commandHandlerProbe = TestProbe.apply(commandHandlerName, actorSystem);
        assertThat(commandHandlerProbe.ref().path().toStringWithoutAddress())
                .isEqualTo(TestConstants.MAPPING_CONFIG.getSignalEnrichmentProviderPath());

        new TestKit(actorSystem) {{
            // GIVEN: MessageMappingProcessor started with a test probe as the configured enrichment provider
            final ActorRef underTest = createMessageMappingProcessorActor(this);

            // WHEN: a signal is received with 2 targets, one with enrichment and one without
            final JsonFieldSelector extraFields = JsonFieldSelector.newInstance("attributes/x", "attributes/y");
            final AuthorizationSubject targetAuthSubject = AuthorizationSubject.newInstance("target:auth-subject");
            final Target targetWithEnrichment = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address")
                    .authorizationContext(AuthorizationContext.newInstance(targetAuthSubject))
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

            // THEN: a mapped signal without enrichment arrives first
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal, targetWithoutEnrichment);

            // THEN: MessageMappingProcessor loads a signal-enrichment-facade lazily
            commandHandlerProbe.expectMsg(MessageMappingProcessorActor.Request.GET_SIGNAL_ENRICHMENT_PROVIDER);
            commandHandlerProbe.reply((ConnectivitySignalEnrichmentProvider)
                    connectionId -> ByRoundTripSignalEnrichmentFacade.of(getRef(), Duration.ofSeconds(10L)));

            // THEN: Receive a RetrieveThing command from the facade.
            final RetrieveThing retrieveThing = expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(extraFields);
            assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext()).containsExactly(targetAuthSubject);
            final JsonObject extra = JsonObject.newBuilder().set("/attributes/x", 5).build();
            reply(RetrieveThingResponse.of(retrieveThing.getEntityId(), extra, retrieveThing.getDittoHeaders()));

            // THEN: Receive an outbound signal with extra fields.
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal, targetWithEnrichment,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra));
        }};
    }

    @SafeVarargs
    private static void expectPublishedMappedMessage(final PublishMappedMessage publishMappedMessage,
            final Signal<?> signal,
            final Target target,
            final Consumer<OutboundSignal.Mapped>... otherAssertionConsumers) {
        assertThat(publishMappedMessage.getOutboundSignal().getSource()).isEqualTo(signal);
        assertThat(publishMappedMessage.getOutboundSignal().getTargets()).containsExactly(target);
        Arrays.asList(otherAssertionConsumers)
                .forEach(con -> con.accept(publishMappedMessage.getOutboundSignal()));
    }

    @Test
    public void testSignalEnrichmentWithPayloadMappedTargets() {
        // GIVEN: test probe actor started with configured values
        // Reset test kit test actor ID to ensure name of test probe matches configuration
        TestKit$.MODULE$.testActorId().set(0);
        final String userGuardianPrefix = "/system/";
        final String testProbeSuffix = "-1";
        final String commandHandlerPath = TestConstants.MAPPING_CONFIG.getSignalEnrichmentProviderPath();
        assertThat(commandHandlerPath).startsWith(userGuardianPrefix).endsWith(testProbeSuffix);
        final String commandHandlerName = commandHandlerPath.substring(userGuardianPrefix.length(),
                commandHandlerPath.length() - testProbeSuffix.length());
        final TestProbe commandHandlerProbe = TestProbe.apply(commandHandlerName, actorSystem);
        assertThat(commandHandlerProbe.ref().path().toStringWithoutAddress())
                .isEqualTo(TestConstants.MAPPING_CONFIG.getSignalEnrichmentProviderPath());

        final CacheConfig cacheConfig =
                DefaultCacheConfig.of(ConfigFactory.parseString("my-cache {\n" +
                        "  maximum-size = 10\n" +
                        "  expire-after-write = 2m\n" +
                        "  expire-after-access = 2m\n" +
                        "}"), "my-cache");

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
            final Target targetWithEnrichment = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address")
                    .authorizationContext(AuthorizationContext.newInstance(targetAuthSubject))
                    .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                            .withExtraFields(extraFields)
                            .build())
                    .build();
            final Target targetWithEnrichmentAnd1PayloadMapper = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address/mapped/1")
                    .authorizationContext(AuthorizationContext.newInstance(targetAuthSubject))
                    .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                            .withExtraFields(extraFields)
                            .build())
                    .payloadMapping(ConnectivityModelFactory.newPayloadMapping(ADD_HEADER_MAPPER))
                    .build();
            final Target targetWithEnrichmentAnd2PayloadMappers = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address/mapped/2")
                    .authorizationContext(AuthorizationContext.newInstance(targetAuthSubject))
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
                            .payloadMapping(ConnectivityModelFactory.newPayloadMapping(ADD_HEADER_MAPPER, DUPLICATING_MAPPER))
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

            // THEN: a mapped signal without enrichment arrives first
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal, targetWithoutEnrichment,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );

            // THEN: a mapped signal without enrichment and applied 1 payload mapper arrives
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal,
                    targetWithoutEnrichmentAnd1PayloadMapper,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders())
                            .contains(AddHeaderMessageMapper.OUTBOUND_HEADER)
            );

            // THEN: a mapped signal without enrichment and applied 2 payload mappers arrives causing 3 messages
            //  as 1 mapper duplicates the message
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal,
                    targetWithoutEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders())
                            .contains(AddHeaderMessageMapper.OUTBOUND_HEADER)
            );
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal,
                    targetWithoutEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal,
                    targetWithoutEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );

            // THEN: MessageMappingProcessor loads a signal-enrichment-facade lazily
            commandHandlerProbe.expectMsg(MessageMappingProcessorActor.Request.GET_SIGNAL_ENRICHMENT_PROVIDER);
            commandHandlerProbe.reply((ConnectivitySignalEnrichmentProvider) connectionId ->
                    CachingSignalEnrichmentFacade.of(getRef(), Duration.ofSeconds(10L), cacheConfig,
                            getSystem().getDispatcher(), "test"));

            // THEN: Receive a RetrieveThing command from the facade.
            final RetrieveThing retrieveThing = expectMsgClass(RetrieveThing.class);
            final JsonFieldSelector extraFieldsWithAdditionalCachingSelectedOnes = JsonFactory.newFieldSelectorBuilder()
                    .addPointers(extraFields)
                    .addFieldDefinition(Thing.JsonFields.POLICY_ID) // additionally always select the policyId
                    .addFieldDefinition(Thing.JsonFields.REVISION) // additionally always select the revision
                    .build();
            assertThat(retrieveThing.getSelectedFields()).contains(extraFieldsWithAdditionalCachingSelectedOnes);
            assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext()).containsExactly(targetAuthSubject);
            final JsonObject extra = JsonObject.newBuilder()
                    .set("/attributes/x", 5)
                    .build();
            final JsonObject extraForCachingFacade = JsonObject.newBuilder()
                    .set("_revision", 8)
                    .set("policyId", TestConstants.Things.THING_ID.toString())
                    .setAll(extra)
                    .build();
            reply(RetrieveThingResponse.of(retrieveThing.getEntityId(), extraForCachingFacade,
                    retrieveThing.getDittoHeaders()));

            // THEN: Receive an outbound signal with extra fields.
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal,
                    targetWithEnrichment,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );

            // THEN: Receive an outbound signal with extra fields and with mapped payload.
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal,
                    targetWithEnrichmentAnd1PayloadMapper,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders())
                            .contains(AddHeaderMessageMapper.OUTBOUND_HEADER)
            );

            // THEN: a mapped signal with enrichment and applied 2 payload mappers arrives causing 3 messages
            //  as 1 mapper duplicates the message
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal,
                    targetWithEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal,
                    targetWithEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).isEmpty()
            );
            expectPublishedMappedMessage(expectMsgClass(PublishMappedMessage.class), signal,
                    targetWithEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders())
                            .contains(AddHeaderMessageMapper.OUTBOUND_HEADER)
            );
        }};
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
                            .withInternalHeaders(DittoHeaders.newBuilder()
                                    .replyTarget(0)
                                    .build())
                            .build();

            messageMappingProcessorActor.tell(externalMessage, getRef());

            if (expectSuccess) {
                final ModifyAttribute modifyAttribute = expectMsgClass(ModifyAttribute.class);
                assertThat(modifyAttribute.getType()).isEqualTo(ModifyAttribute.TYPE);
                assertThat(modifyAttribute.getDittoHeaders().getCorrelationId()).contains(
                        modifyCommand.getDittoHeaders().getCorrelationId().orElse(null));
                assertThat(modifyAttribute.getDittoHeaders().getAuthorizationContext())
                        .isEqualTo(AUTHORIZATION_CONTEXT);
                // thing ID is included in the header for error reporting
                assertThat(modifyAttribute.getDittoHeaders())
                        .extracting(headers -> headers.get(MessageHeaderDefinition.THING_ID.getKey()))
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
                        expectMsgClass(PublishMappedMessage.class).getOutboundSignal();

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
        final AuthorizationContext contextWithPlaceholders = AuthorizationModelFactory.newAuthContext(
                AuthorizationModelFactory.newAuthSubject(
                        "integration:{{header:correlation-id}}:hub-{{   header:content-type   }}"),
                AuthorizationModelFactory.newAuthSubject(
                        "integration:{{header:content-type}}:hub-{{ header:correlation-id }}"));

        final AuthorizationContext expectedAuthContext = AuthorizationModelFactory.newAuthContext(
                AuthorizationModelFactory.newAuthSubject("integration:" + correlationId + ":hub-application/json"),
                AuthorizationModelFactory.newAuthSubject("integration:application/json:hub-" + correlationId));

        testMessageMapping(correlationId, contextWithPlaceholders, ModifyAttribute.class, modifyAttribute -> {
            assertThat(modifyAttribute.getType()).isEqualTo(ModifyAttribute.TYPE);
            assertThat(modifyAttribute.getDittoHeaders().getCorrelationId()).contains(correlationId);
            assertThat(modifyAttribute.getDittoHeaders().getAuthorizationContext()).isEqualTo(expectedAuthContext);

            // mapped by source <- {{ request:subjectId }}
            assertThat(modifyAttribute.getDittoHeaders().get("source"))
                    .contains("integration:" + correlationId + ":hub-application/json");
        });
    }

    @Test
    public void testHeadersOnTwinTopicPathCombinationError() {
        final String correlationId = UUID.randomUUID().toString();

        final AuthorizationContext authorizationContext = AuthorizationModelFactory.newAuthContext(
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
                            .withText(messageContent)
                            .withAuthorizationContext(authorizationContext)
                            .build();

            messageMappingProcessorActor.tell(inboundMessage, getRef());

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
    public void testTopicOnLiveTopicPathCombinationError() {
        final String correlationId = UUID.randomUUID().toString();

        final AuthorizationContext authorizationContext = AuthorizationModelFactory.newAuthContext(
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
                            .withText(messageContent)
                            .withAuthorizationContext(authorizationContext)
                            .build();

            messageMappingProcessorActor.tell(inboundMessage, getRef());

            // THEN: resulting error response retains the topic including thing ID and channel
            final ExternalMessage outboundMessage =
                    expectMsgClass(PublishMappedMessage.class).getOutboundSignal().getExternalMessage();
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
                AuthorizationModelFactory.newAuthSubject("integration:" + placeholder));

        testMessageMapping(UUID.randomUUID().toString(), contextWithUnknownPlaceholder,
                PublishMappedMessage.class, error -> {
                    final OutboundSignal.Mapped outboundSignal = error.getOutboundSignal();
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
                    .withTopicPath(adaptable.getTopicPath())
                    .withText(adaptable.toJsonString())
                    .withAuthorizationContext(context)
                    .withHeaderMapping(CORRELATION_ID_AND_SOURCE_HEADER_MAPPING)
                    .build();

            messageMappingProcessorActor.tell(externalMessage, getRef());

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
                            DittoHeaders.newBuilder()
                                    .correlationId(correlationId)
                                    .build());

            messageMappingProcessorActor.tell(commandResponse, getRef());

            final OutboundSignal.Mapped outboundSignal =
                    expectMsgClass(PublishMappedMessage.class).getOutboundSignal();
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
                            .dittoHeaders(DittoHeaders.newBuilder()
                                    .correlationId(correlationId)
                                    .putHeader(MessageHeaderDefinition.THING_ID.getKey(), KNOWN_THING_ID)
                                    .build())
                            .build();

            messageMappingProcessorActor.tell(thingNotAccessibleException, getRef());

            final OutboundSignal.Mapped outboundSignal =
                    expectMsgClass(PublishMappedMessage.class).getOutboundSignal();

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

    private ActorRef createMessageMappingProcessorActor(final TestKit kit) {
        final Props props =
                MessageMappingProcessorActor.props(kit.getRef(), kit.getRef(), getMessageMappingProcessor(),
                        CONNECTION_ID);
        return actorSystem.actorOf(props);
    }

    private MessageMappingProcessor getMessageMappingProcessor() {
        final Map<String, MappingContext> mappingDefinitions = new HashMap<>();
        mappingDefinitions.put(FAULTY_MAPPER, FaultyMessageMapper.CONTEXT);
        mappingDefinitions.put(ADD_HEADER_MAPPER, AddHeaderMessageMapper.CONTEXT);
        mappingDefinitions.put(DUPLICATING_MAPPER, DuplicatingMessageMapper.CONTEXT);
        final PayloadMappingDefinition payloadMappingDefinition =
                ConnectivityModelFactory.newPayloadMappingDefinition(mappingDefinitions);
        return MessageMappingProcessor.of(CONNECTION_ID, payloadMappingDefinition, actorSystem,
                TestConstants.CONNECTIVITY_CONFIG,
                protocolAdapterProvider, Mockito.mock(DiagnosticLoggingAdapter.class));
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
