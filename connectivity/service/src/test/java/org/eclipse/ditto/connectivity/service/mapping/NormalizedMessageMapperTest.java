/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.eclipse.ditto.things.model.signals.events.FeatureDeleted;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyDeleted;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyModified;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Tests {@link NormalizedMessageMapper}.
 */
public final class NormalizedMessageMapperTest {

    private static final ProtocolAdapter ADAPTER = DittoProtocolAdapter.newInstance();

    private MessageMapper underTest;
    private Connection connection;
    private ConnectivityConfig connectivityConfig;
    private ActorSystem actorSystem;

    @Before
    public void setUp() {
        final Config config = ConfigFactory.load("mapping-test")
                .atKey("ditto.connectivity.mapping")
                .withFallback(ConfigFactory.load("test"));
        connectivityConfig =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(config));
        connection = TestConstants.createConnection();
        underTest = new NormalizedMessageMapper();
        actorSystem = ActorSystem.create("Test", config);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            actorSystem.terminate();
        }
    }

    @Test
    public void thingCreated() {
        final ThingCreated event = ThingCreated.of(ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("thing:created"))
                .setPolicyId(PolicyId.of("thing:created"))
                .setAttributes(Attributes.newBuilder().set("x", 5).build())
                .setFeatures(Features.newBuilder().set(Feature.newBuilder()
                        .properties(JsonObject.of("{\"y\":6}"))
                        .withId("feature")
                        .build()))
                .build(), 1L, Instant.ofEpochSecond(1L), DittoHeaders.empty(), null);

        final Adaptable adaptable = ADAPTER.toAdaptable(event);

        Assertions.assertThat(mapToJson(adaptable))
                .isEqualTo(JsonObject.of("{\n" +
                        "  \"thingId\": \"thing:created\",\n" +
                        "  \"policyId\": \"thing:created\",\n" +
                        "  \"attributes\": {\n" +
                        "    \"x\": 5\n" +
                        "  },\n" +
                        "  \"features\": {\n" +
                        "    \"feature\": {\n" +
                        "      \"properties\": {\n" +
                        "        \"y\": 6\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"_modified\": \"1970-01-01T00:00:01Z\",\n" +
                        "  \"_revision\": 1,\n" +
                        "  \"_context\": {\n" +
                        "    \"topic\": \"thing/created/things/twin/events/created\",\n" +
                        "    \"path\": \"/\",\n" +
                        "    \"headers\": {\n" +
                        "      \"response-required\": \"false\",\n" +
                        "      \"content-type\": \"application/json\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"));
    }

    @Test
    public void thingMerged() {
        final JsonObject mergedObject = ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("thing:merged"))
                .setAttributes(Attributes.newBuilder().set("x", 5).build())
                .setFeatures(Features.newBuilder().set(Feature.newBuilder()
                        .properties(JsonObject.of("{\"y\":6}"))
                        .withId("feature")
                        .build()))
                .build().toJson();
        final ThingMerged event = ThingMerged.of(ThingId.of("thing:merged"), JsonPointer.empty(), mergedObject,
                1L, Instant.ofEpochSecond(1L), DittoHeaders.empty(), null);

        final Adaptable adaptable = ADAPTER.toAdaptable(event);

        Assertions.assertThat(mapToJson(adaptable))
                .isEqualTo(JsonObject.of("{\n" +
                        "  \"thingId\": \"thing:merged\",\n" +
                        "  \"attributes\": {\n" +
                        "    \"x\": 5\n" +
                        "  },\n" +
                        "  \"features\": {\n" +
                        "    \"feature\": {\n" +
                        "      \"properties\": {\n" +
                        "        \"y\": 6\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"_modified\": \"1970-01-01T00:00:01Z\",\n" +
                        "  \"_revision\": 1,\n" +
                        "  \"_context\": {\n" +
                        "    \"topic\": \"thing/merged/things/twin/events/merged\",\n" +
                        "    \"path\": \"/\",\n" +
                        "    \"headers\": {\n" +
                        "      \"response-required\": \"false\",\n" +
                        "      \"content-type\": \"application/merge-patch+json\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"));
    }

    @Test
    public void thingMergedWithNullValues() {
        final JsonObject mergedObject = ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("thing:merged"))
                .setAttributes(Attributes.newBuilder()
                        .set("x", 5)
                        .set("nullValue", JsonValue.nullLiteral())
                        .build())
                .setFeatures(Features.newBuilder().set(Feature.newBuilder()
                        .properties(JsonObject.of("{\"y\":6, \"z\": null}"))
                        .withId("feature")
                        .build()))
                .build().toJson();
        final ThingMerged event = ThingMerged.of(ThingId.of("thing:merged"), JsonPointer.empty(), mergedObject,
                1L, Instant.ofEpochSecond(1L), DittoHeaders.empty(), null);

        final Adaptable adaptable = ADAPTER.toAdaptable(event);

        Assertions.assertThat(mapToJson(adaptable))
                .isEqualTo(JsonObject.of("{\n" +
                        "  \"thingId\": \"thing:merged\",\n" +
                        "  \"attributes\": {\n" +
                        "    \"x\": 5\n" +
                        "  },\n" +
                        "  \"features\": {\n" +
                        "    \"feature\": {\n" +
                        "      \"properties\": {\n" +
                        "        \"y\": 6\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"_modified\": \"1970-01-01T00:00:01Z\",\n" +
                        "  \"_revision\": 1,\n" +
                        "  \"_context\": {\n" +
                        "    \"topic\": \"thing/merged/things/twin/events/merged\",\n" +
                        "    \"path\": \"/\",\n" +
                        "    \"headers\": {\n" +
                        "      \"response-required\": \"false\",\n" +
                        "      \"content-type\": \"application/merge-patch+json\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"));
    }

    @Test
    public void thingMergedWithOnlyNullValues() {
        final JsonObject mergedObject = ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("thing:merged"))
                .setAttributes(Attributes.newBuilder()
                        .set("nullValue", JsonValue.nullLiteral())
                        .build())
                .setFeatures(Features.newBuilder().set(Feature.newBuilder()
                        .properties(JsonObject.of("{\"z\": null}"))
                        .withId("feature")
                        .build()))
                .build().toJson();
        final ThingMerged event = ThingMerged.of(ThingId.of("thing:merged"), JsonPointer.empty(), mergedObject,
                1L, Instant.ofEpochSecond(1L), DittoHeaders.empty(), null);

        final Adaptable adaptable = ADAPTER.toAdaptable(event);

        Assertions.assertThat(mapToJson(adaptable))
                .isEqualTo(JsonObject.of("{\n" +
                        "  \"thingId\": \"thing:merged\",\n" +
                        "  \"_modified\": \"1970-01-01T00:00:01Z\",\n" +
                        "  \"_revision\": 1,\n" +
                        "  \"_context\": {\n" +
                        "    \"topic\": \"thing/merged/things/twin/events/merged\",\n" +
                        "    \"path\": \"/\",\n" +
                        "    \"headers\": {\n" +
                        "      \"response-required\": \"false\",\n" +
                        "      \"content-type\": \"application/merge-patch+json\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"));
    }

    @Test
    public void featurePropertyModified() {
        final FeaturePropertyModified event = FeaturePropertyModified.of(
                ThingId.of("thing:id"),
                "featureId",
                JsonPointer.of("/the/quick/brown/fox/jumps/over/the/lazy/dog"),
                JsonValue.of(9),
                2L,
                Instant.ofEpochSecond(2L),
                DittoHeaders.empty(),
                null);

        final Adaptable adaptable = ADAPTER.toAdaptable(event);

        Assertions.assertThat(mapToJson(adaptable))
                .isEqualTo(JsonObject.of("{\n" +
                        "  \"thingId\": \"thing:id\",\n" +
                        "  \"features\": {\n" +
                        "    \"featureId\": {\n" +
                        "      \"properties\": {\"the\":{\"quick\":{\"brown\":{\"fox\":{\"jumps\":{\"over\":{" +
                        "        \"the\":{\"lazy\":{\"dog\":9}}}}}}}}}\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"_modified\": \"1970-01-01T00:00:02Z\",\n" +
                        "  \"_revision\": 2,\n" +
                        "  \"_context\": {\n" +
                        "    \"topic\": \"thing/id/things/twin/events/modified\",\n" +
                        "    \"path\": \"/features/featureId/properties/the/quick/brown/fox/jumps/over/the/lazy/dog\",\n" +
                        "    \"headers\": {\n" +
                        "      \"response-required\": \"false\",\n" +
                        "      \"content-type\": \"application/json\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"));
    }

    @Test
    public void headersFromAdaptableAreNotMapped() {
        final FeaturePropertyModified event = FeaturePropertyModified.of(
                ThingId.of("thing:id"),
                "featureId",
                JsonPointer.of("/the/quick/brown/fox/jumps/over/the/lazy/dog"),
                JsonValue.of(9),
                2L,
                Instant.ofEpochSecond(2L),
                DittoHeaders.newBuilder().putHeader("random", "header").build(),
                null);

        final Adaptable adaptable = ADAPTER.toAdaptable(event, TopicPath.Channel.TWIN);

        Assertions.assertThat(underTest.map(adaptable).get(0).getHeaders())
                .contains(Map.entry("content-type", "application/json"));
    }

    @Test
    public void withFieldSelection() {
        final Signal<?> event = FeaturePropertyModified.of(
                ThingId.of("thing:id"),
                "featureId",
                JsonPointer.of("/the/quick/brown/fox/jumps/over/the/lazy/dog"),
                JsonValue.of(9),
                2L,
                Instant.ofEpochSecond(2L),
                DittoHeaders.empty(),
                null);

        final Map<String, JsonValue> options = Map.of(NormalizedMessageMapper.FIELDS, JsonValue.of(
                "_modified,_context/topic,_context/headers/content-type,nonexistent/json/pointer"));
        underTest.configure(connection, connectivityConfig,
                DefaultMessageMapperConfiguration.of("normalizer", options, Map.of(), Map.of()),
                actorSystem);

        final Adaptable adaptable = ADAPTER.toAdaptable(event);

        Assertions.assertThat(mapToJson(adaptable))
                .isEqualTo(JsonObject.of("{\n" +
                        "  \"_modified\": \"1970-01-01T00:00:02Z\",\n" +
                        "  \"_context\": {\n" +
                        "    \"topic\": \"thing/id/things/twin/events/modified\",\n" +
                        "    \"headers\": {\n" +
                        "      \"content-type\": \"application/json\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"));
    }

    @Test
    public void withFullThingPayloadFieldSelection() {
        final ThingCreated event = ThingCreated.of(ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("thing:created"))
                .setPolicyId(PolicyId.of("thing:created"))
                .setAttributes(Attributes.newBuilder().set("x", 5).build())
                .setFeatures(Features.newBuilder().set(Feature.newBuilder()
                        .properties(JsonObject.of("{\"y\":6}"))
                        .withId("feature")
                        .build()))
                .build(), 1L, Instant.ofEpochSecond(1L), DittoHeaders.empty(), null);

        final Map<String, JsonValue> options = Map.of(NormalizedMessageMapper.FIELDS, JsonValue.of(
                "thingId,policyId,attributes,features,_modified,_revision,_context(topic,path)," +
                        "_context/headers/correlation-id"));
        underTest.configure(connection, connectivityConfig,
                DefaultMessageMapperConfiguration.of("normalizer", options, Map.of(), Map.of()),
                actorSystem);

        final Adaptable adaptable = ADAPTER.toAdaptable(event);

        Assertions.assertThat(mapToJson(adaptable))
                .isEqualTo(JsonObject.of("{\n" +
                        "  \"thingId\": \"thing:created\",\n" +
                        "  \"policyId\": \"thing:created\",\n" +
                        "  \"attributes\": {\"x\": 5},\n" +
                        "  \"features\":{\"feature\":{\"properties\":{\"y\":6}}},\n" +
                        "  \"_modified\": \"1970-01-01T00:00:01Z\",\n" +
                        "  \"_revision\": 1,\n" +
                        "  \"_context\": {\n" +
                        "    \"topic\": \"thing/created/things/twin/events/created\",\n" +
                        "    \"path\": \"/\"\n" +
                        "  }\n" +
                        "}"));
    }

    @Test
    public void withExtraFieldsBeingIncluded() {
        final ThingId thingId = ThingId.of("thing:feature-modified");
        final ThingEvent<?> event =
                FeaturePropertyModified.of(thingId, "my-feature", JsonPointer.of("abc"), JsonValue.of(false), 2L,
                        Instant.ofEpochSecond(1L), DittoHeaders.empty(), null);

        final Map<String, JsonValue> options = Map.of(NormalizedMessageMapper.FIELDS, JsonValue.of(
                "thingId,policyId,attributes/foo,features,_modified,_revision"));
        underTest.configure(connection, connectivityConfig,
                DefaultMessageMapperConfiguration.of("normalizer", options, Map.of(), Map.of()),
                actorSystem);

        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPolicyId(PolicyId.of(thingId))
                .setAttributes(Attributes.newBuilder()
                        .set("some-attr", 42)
                        .set("foo", "bar")
                        .build())
                .setFeature(Feature.newBuilder()
                        .properties(JsonObject.of("{\"abc\":false,\"def\":true}"))
                        .withId("my-feature")
                        .build())
                .build();

        final Adaptable adaptable = ADAPTER.toAdaptable(event);
        final Adaptable adaptableWithExtra = ProtocolFactory.setExtra(adaptable, thing.toJson(
                JsonFactory.newFieldSelector("policyId,attributes,features/my-feature/properties/def",
                        JsonParseOptions.newBuilder().withoutUrlDecoding().build())));

        Assertions.assertThat(mapToJson(adaptableWithExtra))
                .isEqualTo(JsonObject.of("{\n" +
                        "  \"thingId\": \"thing:feature-modified\",\n" +
                        "  \"policyId\": \"thing:feature-modified\",\n" +
                        "  \"attributes\": {\"foo\": \"bar\"},\n" +
                        "  \"features\":{\"my-feature\":{\"properties\":{\"abc\":false,\"def\":true}}},\n" +
                        "  \"_modified\": \"1970-01-01T00:00:01Z\",\n" +
                        "  \"_revision\": 2\n" +
                        "}"));
    }

    @Test
    public void deletedEventsAreNotMapped() {
        assertNotMapped(AttributeDeleted.of(ThingId.of("thing:id"), JsonPointer.of("/the/quick/brown/fox/"), 3L,
                Instant.ofEpochSecond(3L), DittoHeaders.empty(), null));
        assertNotMapped(FeaturePropertyDeleted.of(ThingId.of("thing:id"), "featureId",
                JsonPointer.of("jumps/over/the/lazy/dog"), 4L, Instant.ofEpochSecond(4L), DittoHeaders.empty(), null));
        assertNotMapped(FeatureDeleted.of(ThingId.of("thing:id"), "featureId", 5L, Instant.EPOCH,
                DittoHeaders.empty(), null));
        assertNotMapped(ThingDeleted.of(ThingId.of("thing:id"), 6L, Instant.EPOCH, DittoHeaders.empty(), null));
    }

    @Test
    public void nonThingEventsAreNotMapped() {
        // command
        assertNotMapped(DeleteThing.of(ThingId.of("thing:id"), DittoHeaders.empty()));

        // response
        assertNotMapped(ModifyThingResponse.modified(ThingId.of("thing:id"), DittoHeaders.empty()));

        // message
        assertNotMapped(SendClaimMessage.of(ThingId.of("thing:id"),
                Message.newBuilder(
                        MessageHeaders.newBuilder(MessageDirection.TO, ThingId.of("thing:id"), "subject").build()
                ).build(),
                DittoHeaders.newBuilder().channel(TopicPath.Channel.LIVE.getName()).build()
        ));
    }

    private void assertNotMapped(final Signal signal) {
        assertThat(underTest.map(ADAPTER.toAdaptable(signal))).isEmpty();
    }

    private JsonObject mapToJson(final Adaptable message) {
        return underTest.map(message)
                .stream()
                .findFirst()
                .flatMap(ExternalMessage::getTextPayload)
                .map(JsonObject::of)
                .orElse(JsonObject.empty());
    }

}
