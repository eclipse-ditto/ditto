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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeader;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link UpdateTwinWithLiveResponseMessageMapper}.
 */
public final class UpdateTwinWithLiveResponseMessageMapperTest {

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private static final AuthorizationContext KNOWN_EXTERNAL_MESSAGE_AUTHORIZATION_CONTEXT =
            AuthorizationContext.newInstance(
                    DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                    AuthorizationSubject.newInstance("pre-authenticated:test-connection"));

    private static final ThingId KNOWN_THING_ID = ThingId.generateRandom();
    private static final String KNOWN_FEATURE_ID = "a-feature";
    private static final FeatureProperties KNOWN_FEATURE_PROPERTIES = FeatureProperties.newBuilder()
            .set("a", "bar")
            .set("b", 42)
            .build();

    private static final MetadataHeaderKey KNOWN_METADATA_HEADER_KEY_0 = MetadataHeaderKey.parse("*/updated-by");
    private static final MetadataHeaderKey KNOWN_METADATA_HEADER_KEY_1 = MetadataHeaderKey.parse("*/updated-via");
    private static final MetadataHeaderKey KNOWN_METADATA_HEADER_KEY_2 = MetadataHeaderKey.parse("*/update-hint");
    private static final MetadataHeaderKey KNOWN_METADATA_HEADER_KEY_3 = MetadataHeaderKey.parse("*/updated-at");
    private static final MetadataHeaderKey KNOWN_METADATA_HEADER_KEY_4 = MetadataHeaderKey.parse("*/unresolved");

    private static final JsonValue KNOWN_METADATA_VALUE_0 = JsonValue.of("{{ request:subjectId }}");
    private static final JsonValue KNOWN_METADATA_VALUE_1 = JsonValue.of("device-live-response");
    private static final String CUSTOM_HEADER_KEY = "some-custom-hint";
    private static final String CUSTOM_HEADER_VALUE = "my-awesome-hint";
    private static final JsonValue KNOWN_METADATA_VALUE_2 = JsonValue.of("{{ header:" + CUSTOM_HEADER_KEY + " }}");
    private static final JsonValue KNOWN_METADATA_VALUE_3 = JsonValue.of("{{ time:now }}");
    private static final String UNRESOLVED_HEADER_KEY = "unresolvable-header";
    private static final String UNRESOLVABLE_HEADER_DEFAULT_FALLBACK = "none";
    private static final JsonValue KNOWN_METADATA_VALUE_4 = JsonValue.of("{{header:" + UNRESOLVED_HEADER_KEY +
            "|fn:default('" + UNRESOLVABLE_HEADER_DEFAULT_FALLBACK + "')}}");
    private static final MetadataHeaders KNOWN_METADATA_HEADERS = MetadataHeaders.newInstance();

    static {
        KNOWN_METADATA_HEADERS.add(MetadataHeader.of(KNOWN_METADATA_HEADER_KEY_0, KNOWN_METADATA_VALUE_0));
        KNOWN_METADATA_HEADERS.add(MetadataHeader.of(KNOWN_METADATA_HEADER_KEY_1, KNOWN_METADATA_VALUE_1));
        KNOWN_METADATA_HEADERS.add(MetadataHeader.of(KNOWN_METADATA_HEADER_KEY_2, KNOWN_METADATA_VALUE_2));
        KNOWN_METADATA_HEADERS.add(MetadataHeader.of(KNOWN_METADATA_HEADER_KEY_3, KNOWN_METADATA_VALUE_3));
        KNOWN_METADATA_HEADERS.add(MetadataHeader.of(KNOWN_METADATA_HEADER_KEY_4, KNOWN_METADATA_VALUE_4));
    }

    private Connection connection;
    private ActorSystem actorSystem;
    private MessageMapper underTest;

    @Before
    public void setUp() {
        connection = TestConstants.createConnection();
        actorSystem = ActorSystem.create("Test", TestConstants.CONFIG);
        underTest = new UpdateTwinWithLiveResponseMessageMapper(actorSystem, Mockito.mock(Config.class));
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            actorSystem.terminate();
            actorSystem = null;
        }
    }

    @Test
    public void mapNonJsonToEmptyMappingResult() {
        final Map<String, String> headers = new HashMap<>();

        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG, createMapperConfig(Map.of()), actorSystem);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withText("foobar")
                .build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void mapTwinResponseToEmptyMappingResult() {
        final List<Adaptable> result = mapResponse(
                createRetrieveAttributeResponse("cid", TopicPath.Channel.TWIN),
                KNOWN_EXTERNAL_MESSAGE_AUTHORIZATION_CONTEXT, Map.of(), Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    public void mapRetrieveThingTwinResponseToEmptyMappingResult() {
        final List<Adaptable> result = mapResponse(
                createRetrieveThingResponse("cid", TopicPath.Channel.TWIN),
                KNOWN_EXTERNAL_MESSAGE_AUTHORIZATION_CONTEXT, Map.of(), Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    public void mapRetrieveThingLiveResponseToExpectedMergeThingMappingResult() {
        final String correlationId = "mapRetrieveThingLiveResponseToExpectedMergeThingMappingResult";
        final List<Adaptable> result = mapResponse(
                createRetrieveThingResponse(correlationId, TopicPath.Channel.LIVE),
                KNOWN_EXTERNAL_MESSAGE_AUTHORIZATION_CONTEXT, Map.of(), Map.of());
        assertThat(result).isNotEmpty();

        final Signal<?> firstMappedSignal = getFirstMappedSignal(result);
        assertThat(firstMappedSignal).isInstanceOf(MergeThing.class);
        final MergeThing mergeThing = (MergeThing) firstMappedSignal;
        assertThat((CharSequence) mergeThing.getResourcePath()).isEqualTo(JsonPointer.empty());
        assertThat(mergeThing.getValue()).isEqualTo(JsonObject.newBuilder()
                .set("/thingId", JsonValue.of(KNOWN_THING_ID))
                .set("/features/" + KNOWN_FEATURE_ID + "/properties", KNOWN_FEATURE_PROPERTIES.toJson())
                .build());
        assertThat(mergeThing.getDittoHeaders().getCorrelationId())
                .contains(correlationId + UpdateTwinWithLiveResponseMessageMapper.CORRELATION_ID_SUFFIX);
        assertThat(mergeThing.getDittoHeaders().getChannel()).isEmpty();
    }

    @Test
    public void mapLiveResponseToExpectedMergeThingMappingResult() {
        final String correlationId = "mapLiveResponseToExpectedMergeThingMappingResult";
        final List<Adaptable> result = mapResponse(
                createRetrieveAttributeResponse(correlationId, TopicPath.Channel.LIVE),
                KNOWN_EXTERNAL_MESSAGE_AUTHORIZATION_CONTEXT, Map.of(), Map.of());
        assertRetrieveAttributeResponseResultedMerge(correlationId, result);
    }

    @Test
    public void mapLiveResponseWithSpecifiedHeadersForMergeToExpectedMergeThingMappingResult() {
        final String correlationId = "mapLiveResponseWithSpecifiedHeadersForMergeToExpectedMergeThingMappingResult";
        final List<Adaptable> result = mapResponse(
                createRetrieveAttributeResponse(correlationId, TopicPath.Channel.LIVE),
                KNOWN_EXTERNAL_MESSAGE_AUTHORIZATION_CONTEXT,
                Map.of(
                        UpdateTwinWithLiveResponseMessageMapper.DITTO_HEADERS_FOR_MERGE,
                        JsonObject.newBuilder()
                                .set(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), JsonValue.of(false))
                                .set(DittoHeaderDefinition.PUT_METADATA.getKey(), KNOWN_METADATA_HEADERS.toJson())
                                .build()
                ),
                Map.of(CUSTOM_HEADER_KEY, CUSTOM_HEADER_VALUE));
        assertRetrieveAttributeResponseResultedMerge(correlationId, result);
        final Signal<?> firstMappedSignal = getFirstMappedSignal(result);
        final MergeThing mergeThing = (MergeThing) firstMappedSignal;
        assertThat(mergeThing.getDittoHeaders().isResponseRequired())
                .isFalse();
        assertThat(mergeThing.getDittoHeaders())
                .containsKey(DittoHeaderDefinition.PUT_METADATA.getKey());
        assertThat(mergeThing.getDittoHeaders().getMetadataHeadersToPut().stream())
                .contains(MetadataHeader.of(KNOWN_METADATA_HEADER_KEY_0,
                        JsonValue.of(KNOWN_EXTERNAL_MESSAGE_AUTHORIZATION_CONTEXT.getFirstAuthorizationSubject()
                                .orElseThrow().toString()))
                )
                .contains(MetadataHeader.of(KNOWN_METADATA_HEADER_KEY_1, KNOWN_METADATA_VALUE_1))
                .matches(l -> l.stream()
                                .filter(mh -> mh.getKey().equals(KNOWN_METADATA_HEADER_KEY_2))
                                .allMatch(mh -> mh.getValue().isString() &&
                                        mh.getValue().asString().equals(CUSTOM_HEADER_VALUE)),
                        "contains '" + KNOWN_METADATA_HEADER_KEY_2 + "' with value " + CUSTOM_HEADER_VALUE
                )
                .matches(l -> l.stream()
                                .filter(mh -> mh.getKey().equals(KNOWN_METADATA_HEADER_KEY_3))
                                .allMatch(mh -> mh.getValue().isString() &&
                                        Instant.parse(mh.getValue().asString()).isBefore(Instant.now())
                                ),
                        "contains '" + KNOWN_METADATA_HEADER_KEY_3 + "' with instant before now"
                )
                .matches(l -> l.stream()
                                .filter(mh -> mh.getKey().equals(KNOWN_METADATA_HEADER_KEY_4))
                                .allMatch(mh -> mh.getValue().isString() &&
                                        mh.getValue().asString().equals(UNRESOLVABLE_HEADER_DEFAULT_FALLBACK)),
                        "contains '" + KNOWN_METADATA_HEADER_KEY_4 + "' with fallback value " +
                                UNRESOLVABLE_HEADER_DEFAULT_FALLBACK
                );
    }

    private static void assertRetrieveAttributeResponseResultedMerge(final String correlationId,
            final List<Adaptable> result) {

        assertThat(result).isNotEmpty();
        final Signal<?> firstMappedSignal = getFirstMappedSignal(result);
        assertThat(firstMappedSignal).isInstanceOf(MergeThing.class);
        final MergeThing mergeThing = (MergeThing) firstMappedSignal;
        assertThat((CharSequence) mergeThing.getResourcePath()).isEqualTo(JsonPointer.of("attributes/foo"));
        assertThat(mergeThing.getValue()).isEqualTo(JsonValue.of("bar"));
        assertThat(mergeThing.getDittoHeaders().getCorrelationId())
                .contains(correlationId + UpdateTwinWithLiveResponseMessageMapper.CORRELATION_ID_SUFFIX);
    }

    private static ThingQueryCommandResponse<?> createRetrieveAttributeResponse(final String correlationId,
            final TopicPath.Channel channel) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel(channel.getName())
                .correlationId(correlationId)
                .build();
        return RetrieveAttributeResponse.of(KNOWN_THING_ID, JsonPointer.of("foo"), JsonValue.of("bar"), dittoHeaders);
    }

    private static ThingQueryCommandResponse<?> createRetrieveThingResponse(final String correlationId,
            final TopicPath.Channel channel) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel(channel.getName())
                .correlationId(correlationId)
                .build();
        return RetrieveThingResponse.of(KNOWN_THING_ID, Thing.newBuilder()
                .setFeature(KNOWN_FEATURE_ID, KNOWN_FEATURE_PROPERTIES)
                .setId(KNOWN_THING_ID)
                .build(), null, null, dittoHeaders);
    }

    private List<Adaptable> mapResponse(final ThingQueryCommandResponse<?> response,
            final AuthorizationContext authorizationContext,
            final Map<String, JsonValue> options,
            final Map<String, String> additionalHeaders) {
        final Map<String, String> headers = new HashMap<>();
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        headers.putAll(additionalHeaders);

        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG, createMapperConfig(options), actorSystem);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withAuthorizationContext(authorizationContext)
                .withText(getJsonifiableAdaptable(response).toJsonString())
                .build();
        return underTest.map(externalMessage);
    }

    private static JsonifiableAdaptable getJsonifiableAdaptable(final Signal<?> signal) {
        return ProtocolFactory.wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(signal));
    }

    private static Signal<?> getFirstMappedSignal(final List<Adaptable> mappingResult) {
        return mappingResult.stream().findFirst()
                .map(DITTO_PROTOCOL_ADAPTER::fromAdaptable)
                .orElseGet(() -> fail("Mapping Result did not contain a Signal."));
    }

    private DefaultMessageMapperConfiguration createMapperConfig(final Map<String, JsonValue> options) {
        return DefaultMessageMapperConfiguration.of("valid", options, Collections.emptyMap(), Collections.emptyMap());
    }

}
