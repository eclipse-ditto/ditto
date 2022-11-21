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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

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

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotDeclaredException;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.base.model.signals.events.AbstractEventsourcedEvent;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.InboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.MessageMappingFailedException;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.EnforcementFactoryFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link InboundMappingSink} and {@link OutboundMappingProcessorActor}.
 */
public final class MessageMappingProcessorActorTest extends AbstractMessageMappingProcessorActorTest {

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
        final EnforcementFilterFactory<String, Signal<?>> factory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(mqttEnforcement, new TestPlaceholder());
        final EnforcementFilter<Signal<?>> enforcementFilter = factory.getFilter("mqtt/topic/my/thing");
        testExternalMessageInDittoProtocolIsProcessed(enforcementFilter);
    }

    @Test
    public void testThingIdEnforcementExternalMessageInDittoProtocolIsProcessedExpectErrorResponse() {
        TestConstants.disableLogging(actorSystem);
        final Enforcement mqttEnforcement =
                ConnectivityModelFactory.newEnforcement("{{ test:placeholder }}",
                        "mqtt/topic/{{ thing:namespace }}/{{ thing:name }}");
        final EnforcementFilterFactory<String, Signal<?>> factory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(mqttEnforcement, new TestPlaceholder());
        final EnforcementFilter<Signal<?>> enforcementFilter = factory.getFilter("some/invalid/target");
        testExternalMessageInDittoProtocolIsProcessed(enforcementFilter, false, null,
                r -> assertThat(r.getDittoRuntimeException())
                        .isInstanceOf(ConnectionSignalIdEnforcementFailedException.class)
        );
    }

    @Test
    public void testMappingFailedExpectErrorResponseWitMapperId() {
        TestConstants.disableLogging(actorSystem);
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
        final TestProbe proxyActorProbe = TestProbe.apply("mockProxyActorProbe", actorSystem);
        setUpProxyActor(proxyActorProbe.ref());

        new TestKit(actorSystem) {{
            // GIVEN: MessageMappingProcessor started with a test probe as the configured enrichment provider
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);

            // WHEN: a signal is received with 2 targets, one with enrichment and one without
            final ThingFieldSelector extraFields = ThingFieldSelector.fromJsonFieldSelector(
                    JsonFieldSelector.newInstance("attributes/x", "attributes/y"));
            final AuthorizationSubject targetAuthSubject = AuthorizationSubject.newInstance("target:auth-subject");
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
            outboundMappingProcessorActor.tell(outboundSignal, getRef());

            // THEN: Receive a RetrieveThing command from the facade.
            final RetrieveThing retrieveThing = proxyActorProbe.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(extraFields);
            assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext()).containsExactly(targetAuthSubject);

            final JsonObject extra = JsonObject.newBuilder().set("/attributes/x", 5).build();
            proxyActorProbe.reply(
                    RetrieveThingResponse.of(retrieveThing.getEntityId(), extra, retrieveThing.getDittoHeaders()));

            // THEN: a mapped signal without enrichment arrives first
            final BaseClientActor.PublishMappedMessage
                    publishMappedMessage = expectMsgClass(BaseClientActor.PublishMappedMessage.class);
            int i = 0;
            expectPublishedMappedMessage(publishMappedMessage, i++, signal, targetWithoutEnrichment);

            // THEN: Receive an outbound signal with extra fields.
            expectPublishedMappedMessage(publishMappedMessage, i, signal, targetWithEnrichment,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra));
        }};
    }

    @Test
    public void testSignalEnrichmentWithPayloadMappedTargets() {
        resetActorSystemWithCachingSignalEnrichmentProvider();
        final TestProbe proxyActorProbe = TestProbe.apply("mockEdgeForwarder", actorSystem);
        setUpProxyActor(proxyActorProbe.ref());

        new TestKit(actorSystem) {{
            // GIVEN: MessageMappingProcessor started with a test probe as the configured enrichment provider
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);

            // WHEN: a signal is received with 6 targets:
            //  - 1 with enrichment w/o payload mapping
            //  - 1 with enrichment with 1 payload mapping
            //  - 1 with enrichment with 2 payload mappings
            //  - 1 w/o enrichment w/o payload mapping
            //  - 1 w/o enrichment with 1 payload mapping
            //  - 1 w/o enrichment with 2 payload mappings
            final JsonFieldSelector extraFields = JsonFactory.newFieldSelector("attributes/x,attributes/y",
                    JsonParseOptions.newBuilder().withoutUrlDecoding().build());
            final ThingFieldSelector thingFieldSelector = ThingFieldSelector.fromJsonFieldSelector(extraFields);
            final AuthorizationSubject targetAuthSubject = AuthorizationSubject.newInstance("target:auth-subject");
            final Target targetWithEnrichment = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address")
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            targetAuthSubject))
                    .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                            .withExtraFields(thingFieldSelector)
                            .build())
                    .build();
            final Target targetWithEnrichmentAnd1PayloadMapper = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address/mapped/1")
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            targetAuthSubject))
                    .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                            .withExtraFields(thingFieldSelector)
                            .build())
                    .payloadMapping(ConnectivityModelFactory.newPayloadMapping(ADD_HEADER_MAPPER))
                    .build();
            final Target targetWithEnrichmentAnd2PayloadMappers = ConnectivityModelFactory.newTargetBuilder()
                    .address("target/address/mapped/2")
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            targetAuthSubject))
                    .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                            .withExtraFields(thingFieldSelector)
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
            final Signal<?> signal =
                    ((AbstractEventsourcedEvent<?>) TestConstants.thingModified(Collections.emptyList()))
                            .setRevision(8L); // important to set revision to same value as cache lookup retrieves
            final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(signal, Arrays.asList(
                    targetWithEnrichment,
                    targetWithoutEnrichment,
                    targetWithEnrichmentAnd1PayloadMapper,
                    targetWithoutEnrichmentAnd1PayloadMapper,
                    targetWithEnrichmentAnd2PayloadMappers,
                    targetWithoutEnrichmentAnd2PayloadMappers)
            );
            outboundMappingProcessorActor.tell(outboundSignal, getRef());
            // THEN: Receive a RetrieveThing command from the facade.
            final RetrieveThing retrieveThing = proxyActorProbe.expectMsgClass(RetrieveThing.class);
            final JsonFieldSelector extraFieldsWithAdditionalCachingSelectedOnes = JsonFactory.newFieldSelectorBuilder()
                    .addPointers(extraFields)
                    .addFieldDefinition(Thing.JsonFields.REVISION) // additionally always select the revision
                    .build();
            assertThat(retrieveThing.getSelectedFields()).contains(extraFieldsWithAdditionalCachingSelectedOnes);
            assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext()).containsExactly(targetAuthSubject);
            final JsonObject extra = JsonObject.newBuilder()
                    .set("/attributes/x", 5)
                    .build();
            final JsonObject extraForCachingFacade = JsonObject.newBuilder()
                    .set("_revision", 8)
                    .setAll(extra)
                    .build();
            proxyActorProbe.reply(RetrieveThingResponse.of(retrieveThing.getEntityId(), extraForCachingFacade,
                    retrieveThing.getDittoHeaders()));

            // THEN: mapped messages arrive in a batch.
            final BaseClientActor.PublishMappedMessage
                    publishMappedMessage = expectMsgClass(BaseClientActor.PublishMappedMessage.class);
            int i = 0;

            // THEN: the first mapped signal is without enrichment
            expectPublishedMappedMessage(publishMappedMessage, i++, signal, targetWithoutEnrichment,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).containsOnlyKeys("content-type")
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
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).containsOnlyKeys("content-type")
            );
            expectPublishedMappedMessage(publishMappedMessage, i++, signal,
                    targetWithoutEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).containsOnlyKeys("content-type")
            );

            // THEN: Receive an outbound signal with extra fields.
            expectPublishedMappedMessage(publishMappedMessage, i++, signal, targetWithEnrichment,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).containsOnlyKeys("content-type")
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
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).containsOnlyKeys("content-type")
            );
            expectPublishedMappedMessage(publishMappedMessage, i++, signal,
                    targetWithEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders()).containsOnlyKeys("content-type")
            );
            expectPublishedMappedMessage(publishMappedMessage, i, signal,
                    targetWithEnrichmentAnd2PayloadMappers,
                    mapped -> assertThat(mapped.getAdaptable().getPayload().getExtra()).contains(extra),
                    mapped -> assertThat(mapped.getExternalMessage().getHeaders())
                            .contains(AddHeaderMessageMapper.OUTBOUND_HEADER)
            );
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

        final AuthorizationContext expectedAuthContext = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationModelFactory.newAuthSubject("integration:" + correlationId + ":hub-application/json"),
                AuthorizationModelFactory.newAuthSubject("integration:application/json:hub-" + correlationId));

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

            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);

            // WHEN: message sent valid topic and invalid topic+path combination
            final String messageContent = "{  \n" +
                    "   \"topic\":\"Testspace/octopus/things/twin/commands/retrieve\",\n" +
                    "   \"path\":\"/invalid\",\n" +
                    "   \"headers\":{  \n" +
                    "      \"correlation-id\":\"" + correlationId + "\"\n" +
                    "   }\n" +
                    "}";
            final ExternalMessage inboundMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withInternalHeaders(HEADERS_WITH_REPLY_INFORMATION)
                            .withText(messageContent)
                            .withAuthorizationContext(authorizationContext)
                            .build();

            final TestProbe collectorProbe = TestProbe.apply("collector", actorSystem);
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(inboundMessage, collectorProbe.ref()),
                    ActorRef.noSender()
            );

            // THEN: resulting error response retains the correlation ID
            final OutboundSignal outboundSignal =
                    expectMsgClass(BaseClientActor.PublishMappedMessage.class).getOutboundSignal();
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

        testMessageMappingWithoutCorrelationId(connectionAuthContext, ModifyAttribute.class, modifyAttribute -> {
            assertThat(modifyAttribute.getType()).isEqualTo(ModifyAttribute.TYPE);
            assertThat(modifyAttribute.getDittoHeaders().getAuthorizationContext()).isEqualTo(connectionAuthContext);
        });
    }

    @Test
    public void testTopicOnLiveTopicPathCombinationError() {
        final String correlationId = UUID.randomUUID().toString();

        final AuthorizationContext authorizationContext = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationModelFactory.newAuthSubject("integration:" + correlationId + ":hub-application/json"));

        new TestKit(actorSystem) {{

            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);

            // WHEN: message sent with valid topic and invalid topic+path combination
            final String topicPrefix = "Testspace/octopus/things/live/";
            final String topic = topicPrefix + "commands/merge";
            final String path = "/policyId";
            final String messageContent = "{  \n" +
                    "   \"topic\":\"" + topic + "\",\n" +
                    "   \"path\":\"" + path + "\"\n" +
                    "}";
            final ExternalMessage inboundMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withInternalHeaders(HEADERS_WITH_REPLY_INFORMATION)
                            .withText(messageContent)
                            .withAuthorizationContext(authorizationContext)
                            .build();

            final TestProbe collectorProbe = TestProbe.apply("collector", actorSystem);
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(inboundMessage, collectorProbe.ref()),
                    ActorRef.noSender()
            );

            // THEN: resulting error response retains the topic including thing ID and channel
            final ExternalMessage outboundMessage =
                    expectMsgClass(BaseClientActor.PublishMappedMessage.class).getOutboundSignal()
                            .first()
                            .getExternalMessage();
            assertThat(outboundMessage)
                    .extracting(e -> JsonFactory.newObject(e.getTextPayload().orElse("{}"))
                            .getValue("topic"))
                    .isEqualTo(Optional.of(JsonValue.of(topicPrefix + "errors")));
        }};
    }

    @Test
    public void testUnknownPlaceholdersExpectUnresolvedPlaceholderException() {
        TestConstants.disableLogging(actorSystem);

        final String placeholderKey = "header:unknown";
        final String placeholder = "{{" + placeholderKey + "}}";
        final AuthorizationContext contextWithUnknownPlaceholder = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationModelFactory.newAuthSubject("integration:" + placeholder));

        testMessageMapping(UUID.randomUUID().toString(), contextWithUnknownPlaceholder,
                BaseClientActor.PublishMappedMessage.class, error -> {
                    final OutboundSignal.Mapped outboundSignal = error.getOutboundSignal().first();
                    final UnresolvedPlaceholderException exception = UnresolvedPlaceholderException.fromMessage(
                            outboundSignal.getExternalMessage()
                                    .getTextPayload()
                                    .orElseThrow(() -> new IllegalArgumentException("payload was empty")),
                            DittoHeaders.of(outboundSignal.getExternalMessage().getHeaders()));
                    assertThat(exception.getMessage()).contains(placeholderKey);
                });
    }

    @Test
    public void testCommandResponseIsProcessed() {
        new TestKit(actorSystem) {{
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);

            final String correlationId = UUID.randomUUID().toString();
            final ModifyAttributeResponse commandResponse =
                    ModifyAttributeResponse.modified(KNOWN_THING_ID, JsonPointer.of("foo"),
                            HEADERS_WITH_REPLY_INFORMATION.toBuilder().correlationId(correlationId).build());

            outboundMappingProcessorActor.tell(commandResponse, getRef());

            final OutboundSignal.Mapped outboundSignal =
                    expectMsgClass(BaseClientActor.PublishMappedMessage.class).getOutboundSignal().first();
            assertThat(outboundSignal.getSource().getDittoHeaders().getCorrelationId())
                    .contains(correlationId);
        }};
    }


    @Test
    public void testThingNotAccessibleExceptionRetainsTopic() {
        new TestKit(actorSystem) {{
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);

            // WHEN: message mapping processor receives ThingNotAccessibleException with thing-id set from topic path
            final String correlationId = UUID.randomUUID().toString();
            final ThingNotAccessibleException thingNotAccessibleException =
                    ThingNotAccessibleException.newBuilder(KNOWN_THING_ID)
                            .dittoHeaders(HEADERS_WITH_REPLY_INFORMATION.toBuilder()
                                    .correlationId(correlationId)
                                    .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), "thing:" + KNOWN_THING_ID)
                                    .build())
                            .build();

            outboundMappingProcessorActor.tell(thingNotAccessibleException, getRef());

            final OutboundSignal.Mapped outboundSignal =
                    expectMsgClass(BaseClientActor.PublishMappedMessage.class).getOutboundSignal().first();

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
    public void testAggregationOfAcknowledgements() {
        new TestKit(actorSystem) {{
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);
            final AcknowledgementRequest signalAck =
                    AcknowledgementRequest.parseAcknowledgementRequest("my-custom-ack-3");
            Set<AcknowledgementRequest> validationSet = new HashSet<>(Collections.singletonList(signalAck));
            validationSet.addAll(CONNECTION.getSources().get(0).getAcknowledgementRequests().map(
                    FilteredAcknowledgementRequest::getIncludes).orElse(Collections.emptySet()));
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
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(message, collectorProbe.ref()),
                    ActorRef.noSender()
            );

            final ModifyAttribute modifyAttribute = fishForMsg(this, ModifyAttribute.class);
            assertThat(modifyAttribute.getDittoHeaders().getAcknowledgementRequests()).isEqualTo(validationSet);
        }};
    }

    @Test
    public void testFilteringOfAcknowledgements() {
        new TestKit(actorSystem) {{
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);
            final AcknowledgementRequest signalAck =
                    AcknowledgementRequest.parseAcknowledgementRequest("my-custom-ack-3");
            final Map<String, String> headers = new HashMap<>();
            headers.put("content-type", "application/json");
            headers.put("qos", "0");
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
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(message,collectorProbe.ref()),
                    ActorRef.noSender()
            );

            final ModifyAttribute modifyAttribute = expectMsgClass(ModifyAttribute.class);
            assertThat(modifyAttribute.getDittoHeaders().getAcknowledgementRequests()).isEmpty();
        }};
    }

    @Test
    public void testAppendingConnectionIdToResponses() {
        new TestKit(actorSystem) {{
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);
            final TestProbe sender = TestProbe.apply("sender", actorSystem);

            // Acknowledgement
            final AcknowledgementLabel label = AcknowledgementLabel.of("label");
            final Acknowledgement acknowledgement =
                    Acknowledgement.of(label, KNOWN_THING_ID, HttpStatus.BAD_REQUEST,
                            DittoHeaders.empty(), JsonValue.of("payload"));
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(toExternalMessage(acknowledgement),
                            sender.ref()),
                    ActorRef.noSender()
            );
            final Acknowledgement receivedAck = (Acknowledgement) expectMsgClass(InboundSignal.class).getSignal();
            assertThat(receivedAck.getDittoHeaders())
                    .containsEntry(DittoHeaderDefinition.CONNECTION_ID.getKey(), CONNECTION_ID.toString());
            assertThat(getLastSender()).isEqualTo(outboundMappingProcessorActor);

            // Acknowledgements
            final Signal<?> acknowledgements = Acknowledgements.of(List.of(acknowledgement), DittoHeaders.empty());
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(toExternalMessage(acknowledgements),
                            sender.ref()),
                    ActorRef.noSender()
            );
            final Acknowledgements receivedAcks = (Acknowledgements) expectMsgClass(InboundSignal.class).getSignal();
            assertThat(receivedAcks.getAcknowledgement(label)
                    .orElseThrow()
                    .getDittoHeaders())
                    .containsEntry(DittoHeaderDefinition.CONNECTION_ID.getKey(), CONNECTION_ID.toString());
            assertThat(getLastSender()).isEqualTo(outboundMappingProcessorActor);

            // Live response
            final Signal<?> liveResponse = DeleteThingResponse.of(KNOWN_THING_ID, DittoHeaders.newBuilder()
                    .channel(TopicPath.Channel.LIVE.getName())
                    .build());
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(toExternalMessage(liveResponse),
                            sender.ref()),
                    ActorRef.noSender()
            );
            final DeleteThingResponse receivedResponse =
                    (DeleteThingResponse) expectMsgClass(InboundSignal.class).getSignal();
            assertThat(receivedResponse.getDittoHeaders().getChannel()).contains(TopicPath.Channel.LIVE.getName());
            assertThat(receivedResponse.getDittoHeaders())
                    .containsEntry(DittoHeaderDefinition.CONNECTION_ID.getKey(), CONNECTION_ID.toString());
            assertThat(getLastSender()).isEqualTo(actorSystem.deadLetters());
        }};
    }

    @Test
    public void sendAckWithoutDeclaration() {
        new TestKit(actorSystem) {{
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);

            // WHEN: message mapping processor actor receives an incoming acknowledgement
            // from a source with reply-target and without declared-acks
            final AcknowledgementLabel label = AcknowledgementLabel.of("label");
            final Acknowledgement acknowledgement =
                    Acknowledgement.of(label, KNOWN_THING_ID, HttpStatus.BAD_REQUEST,
                            DittoHeaders.newBuilder()
                                    .replyTarget(0)
                                    .expectedResponseTypes(ResponseType.ERROR)
                                    .build(),
                            JsonValue.of("payload"));
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(
                            toExternalMessage(acknowledgement, builder -> {}),
                            getRef()),
                    ActorRef.noSender()
            );

            // THEN: ann AcknowledgementLabelNotDeclaredException is published to the reply-target
            final BaseClientActor.PublishMappedMessage errorResponse =
                    (BaseClientActor.PublishMappedMessage) fishForMessage(Duration.ofSeconds(3L),
                            "PublishMappedMessage",
                            BaseClientActor.PublishMappedMessage.class::isInstance);
            final Signal<?> source = errorResponse.getOutboundSignal().getSource();
            assertThat(source).isInstanceOf(ErrorResponse.class);
            assertThat(((ErrorResponse<?>) source).getDittoRuntimeException())
                    .isEqualTo(AcknowledgementLabelNotDeclaredException.of(label, acknowledgement.getDittoHeaders()));
            assertThat(source).isInstanceOf(SignalWithEntityId.class);
            final CharSequence entityId = ((SignalWithEntityId<?>) source).getEntityId();
            assertThat(entityId).isEqualTo(KNOWN_THING_ID);
        }};
    }

    @Test
    public void forwardsCreateSubscriptionToConnectionActor() {
        new TestKit(actorSystem) {{
            final ActorRef outboundMappingProcessorActor = createOutboundMappingProcessorActor(this);
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(this, outboundMappingProcessorActor);
            final Map<String, String> headers = new HashMap<>();
            headers.put("content-type", "application/json");
            final AuthorizationContext context =
                    AuthorizationModelFactory.newAuthContext(
                            DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationModelFactory.newAuthSubject("ditto:ditto"));
            final CreateSubscription createSubscription = CreateSubscription.of(DittoHeaders.empty());
            final JsonifiableAdaptable adaptable = ProtocolFactory
                    .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(createSubscription));
            final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                    .withTopicPath(adaptable.getTopicPath())
                    .withText(adaptable.toJsonString())
                    .withAuthorizationContext(context)
                    .withHeaderMapping(SOURCE_HEADER_MAPPING)
                    .build();

            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(externalMessage, getRef()),
                    ActorRef.noSender()
            );

            connectionActorProbe.expectMsgClass(CreateSubscription.class);
        }};
    }

    @Test
    public void inboundQueryCommandDoesNotRequestAcknowledgements() {
        new TestKit(actorSystem) {{
            final ActorRef inboundMappingProcessorActor =
                    createInboundMappingProcessorActor(actorSystem.deadLetters(), actorSystem.deadLetters(), this);

            // WHEN: inbound mapping processor receives a query command with acknowledgement requests
            final RetrieveFeature retrieveFeature = RetrieveFeature.of(ThingId.of("thing:id"), "featureId",
                    DittoHeaders.newBuilder()
                            .acknowledgementRequest(AcknowledgementRequest.parseAcknowledgementRequest("dummy-request"))
                            .build());
            inboundMappingProcessorActor.tell(
                    new ExternalMessageWithSender(toExternalMessage(retrieveFeature),
                            getRef()),
                    ActorRef.noSender()
            );

            // THEN: the response collector actor is asked to acknowledge right away despite the ack request.
            expectMsg(ResponseCollectorActor.setCount(0));
        }};
    }

}
