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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link MergeThingStrategy}.
 */
public final class MergeThingStrategyTest extends AbstractCommandStrategyTest {

    private MergeThingStrategy underTest;

    @Before
    public void setUp() {
        final ActorSystem system = ActorSystem.create("test", ConfigFactory.load("test"));
        underTest = new MergeThingStrategy(system);
    }

    @Test
    public void mergeThing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();

        final Thing existing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();

        final Thing thingToMerge = ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setAttribute(JsonPointer.of("newAttribute"), JsonValue.of("attributeValue"))
                .setFeature("newFeature",
                        ThingsModelFactory.newFeaturePropertiesBuilder().set("newIntProperty", 123).build())
                .build();

        final JsonPointer path = JsonPointer.of("/");
        final JsonObject thingJson = thingToMerge.toJson();

        final MergeThing mergeThing = MergeThing.of(thingId, path, thingJson, provideHeaders(context));
        final MergeThingResponse expectedCommandResponse =
                ETagTestUtils.mergeThingResponse(existing, path, mergeThing.getDittoHeaders());
        assertStagedModificationResult(underTest, existing, mergeThing, ThingMerged.class, expectedCommandResponse);
    }

    @Test
    public void mergeThingWithLargeAttributeExpectThingTooLargeException() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();
        final Thing existing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();
        final JsonPointer path = Thing.JsonFields.ATTRIBUTES.getPointer().append(JsonPointer.of("large"));
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
        final MergeThing mergeThing = MergeThing.withAttribute(thingId, path,
                JsonValue.of("~".repeat((int) THING_SIZE_LIMIT_BYTES - 150)), dittoHeaders);
        assertThatExceptionOfType(ThingTooLargeException.class)
                .isThrownBy(() -> underTest.apply(context, existing, NEXT_REVISION, mergeThing))
                .satisfies(e -> assertThat(e.getDittoHeaders()).containsAllEntriesOf(dittoHeaders));
    }

    @Test
    public void mergeThingWithPatchConditions() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();

        final Thing existing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();

        final Thing thingToMerge = ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setAttribute(JsonPointer.of("maker"), JsonValue.of("ACME Corp"))
                .setAttribute(JsonPointer.of("location/latitude"), JsonValue.of(50.0))
                .setAttribute(JsonPointer.of("location/longitude"), JsonValue.of(10.0))
                .setAttribute(JsonPointer.of("status"), JsonValue.of("updated"))
                .build();

        final JsonPointer path = JsonPointer.of("/");
        final JsonObject thingJson = thingToMerge.toJson();

        final JsonObject patchConditions = JsonObject.newBuilder()
                .set("attributes/maker", "eq(attributes/maker,\"ACME Corp\")")  // Should fail (existing != "ACME Corp")
                .set("attributes/location/latitude", "gt(attributes/location/latitude,40.0)")  // Should pass (existing > 40)
                .build();

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(UUID.randomUUID().toString())
                .putHeader(DittoHeaderDefinition.MERGE_THING_PATCH_CONDITIONS.getKey(), patchConditions.toString())
                .build();

        final MergeThing mergeThing = MergeThing.of(thingId, path, thingJson, dittoHeaders);
        final MergeThingResponse expectedCommandResponse =
                ETagTestUtils.mergeThingResponse(existing, path, mergeThing.getDittoHeaders());

        final ThingMerged event = assertStagedModificationResult(underTest, existing, mergeThing, ThingMerged.class, expectedCommandResponse);
        
        final JsonValue mergedValue = event.getValue();
        assertThat(mergedValue.isObject()).isTrue();
        final JsonObject mergedObject = mergedValue.asObject();
        
        assertThat(mergedObject.getValue("attributes/maker")).isEmpty();
        assertThat(mergedObject.getValue("attributes/location/latitude")).contains(JsonValue.of(50.0));
        assertThat(mergedObject.getValue("attributes/location/longitude")).contains(JsonValue.of(10.0));
        assertThat(mergedObject.getValue("attributes/status")).contains(JsonValue.of("updated"));
    }

    @Test
    public void mergeThingWithPatchConditionsForFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();

        final Thing existing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();

        final Thing thingToMerge = ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setFeature("FluxCapacitor", ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("target_year_1", 1985)
                        .set("target_year_2", 2020)
                        .set("target_year_3", 1900)
                        .build())
                .setFeature("newFeature", ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("state", "updated")
                        .build())
                .build();

        final JsonPointer path = JsonPointer.of("/");
        final JsonObject thingJson = thingToMerge.toJson();

        final JsonObject patchConditions = JsonObject.newBuilder()
                .set("features/FluxCapacitor/properties/target_year_1", "gt(features/FluxCapacitor/properties/target_year_1,1900)")  // Should pass (1955 > 1900)
                .set("features/FluxCapacitor/properties/target_year_2", "lt(features/FluxCapacitor/properties/target_year_2,2000)")  // Should fail (2015 >= 2000)
                .set("features/FluxCapacitor/properties/target_year_3", "gt(features/FluxCapacitor/properties/target_year_3,1800)")  // Should pass (1885 > 1800)
                .build();

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(UUID.randomUUID().toString())
                .putHeader(DittoHeaderDefinition.MERGE_THING_PATCH_CONDITIONS.getKey(), patchConditions.toString())
                .build();

        final MergeThing mergeThing = MergeThing.of(thingId, path, thingJson, dittoHeaders);
        final MergeThingResponse expectedCommandResponse =
                ETagTestUtils.mergeThingResponse(existing, path, mergeThing.getDittoHeaders());

        final ThingMerged event = assertStagedModificationResult(underTest, existing, mergeThing, ThingMerged.class, expectedCommandResponse);
        
        final JsonValue mergedValue = event.getValue();
        assertThat(mergedValue.isObject()).isTrue();
        final JsonObject mergedObject = mergedValue.asObject();

        assertThat(mergedObject.getValue("features/FluxCapacitor/properties/target_year_1")).contains(JsonValue.of(1985));
        assertThat(mergedObject.getValue("features/FluxCapacitor/properties/target_year_2")).isEmpty();
        assertThat(mergedObject.getValue("features/FluxCapacitor/properties/target_year_3")).contains(JsonValue.of(1900));
        assertThat(mergedObject.getValue("features/newFeature/properties/state")).contains(JsonValue.of("updated"));
    }

    @Test
    public void mergeThingWithPatchConditionsPreservesNullValuesWhenEmptyObjectRemovalEnabled() {
        final Config configWithEmptyRemoval = ConfigFactory.parseString(
                "ditto.things.thing.merge.remove-empty-objects-after-patch-condition-filtering = true"
        ).withFallback(ConfigFactory.load("test"));
        final ActorSystem systemWithEmptyRemoval = ActorSystem.create("test-empty-removal", configWithEmptyRemoval);
        final MergeThingStrategy underTestWithEmptyRemoval = new MergeThingStrategy(systemWithEmptyRemoval);

        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();

        final Thing existing = THING_V2.toBuilder()
                .setRevision(NEXT_REVISION - 1)
                .setFeature("sensor", ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("temperature", 25)
                        .set("humidity", 60)
                        .build())
                .setFeature("display", ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("brightness", 80)
                        .set("contrast", 50)
                        .build())
                .setFeature("temperatureControl", ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("targetTemperature", 19.5)
                        .build())
                .build();

        final JsonObject mergePayload = JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("sensor", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("temperature", JsonFactory.nullLiteral())
                                        .set("humidity", JsonFactory.nullLiteral())
                                        .build())
                                .build())
                        .set("display", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("brightness", JsonFactory.nullLiteral())
                                        .build())
                                .build())
                        .set("temperatureControl", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("targetTemperature", 22.0)
                                        .build())
                                .build())
                        .build())
                .build();

        final JsonObject patchConditions = JsonObject.newBuilder()
                .set("features/sensor/properties/temperature", "gt(features/sensor/properties/temperature,20)")
                .set("features/sensor/properties/humidity", "lt(features/sensor/properties/humidity,50)")
                .set("features/display/properties/brightness", "gt(features/display/properties/brightness,70)")
                .build();

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(UUID.randomUUID().toString())
                .putHeader(DittoHeaderDefinition.MERGE_THING_PATCH_CONDITIONS.getKey(), patchConditions.toString())
                .build();

        final MergeThing mergeThing = MergeThing.of(thingId, JsonPointer.of("/"), mergePayload, dittoHeaders);
        final MergeThingResponse expectedCommandResponse =
                ETagTestUtils.mergeThingResponse(existing, JsonPointer.of("/"), mergeThing.getDittoHeaders());

        final ThingMerged event = assertStagedModificationResult(underTestWithEmptyRemoval, existing, mergeThing,
                ThingMerged.class, expectedCommandResponse);

        final JsonValue mergedValue = event.getValue();
        assertThat(mergedValue.isObject()).isTrue();
        final JsonObject mergedObject = mergedValue.asObject();

        // Verify temperature null is preserved (condition passed: 25 > 20)
        assertThat(mergedObject.getValue("features/sensor/properties/temperature")).contains(JsonFactory.nullLiteral());
        // Verify humidity null was filtered out (condition failed: 60 >= 50), resulting in empty path
        assertThat(mergedObject.getValue("features/sensor/properties/humidity")).isEmpty();
        // Verify brightness null is preserved (condition passed: 80 > 70)
        assertThat(mergedObject.getValue("features/display/properties/brightness")).contains(JsonFactory.nullLiteral());
        // Verify targetTemperature was updated (no condition on this field)
        assertThat(mergedObject.getValue("features/temperatureControl/properties/targetTemperature"))
                .contains(JsonValue.of(22.0));

        systemWithEmptyRemoval.terminate();
    }

    @Test
    public void mergeThingWithPatchConditionsRemovesOnlyEmptyObjectsNotLiteralNulls() {
        final Config configWithEmptyRemoval = ConfigFactory.parseString(
                "ditto.things.thing.merge.remove-empty-objects-after-patch-condition-filtering = true"
        ).withFallback(ConfigFactory.load("test"));
        final ActorSystem systemWithEmptyRemoval = ActorSystem.create("test-empty-removal-2", configWithEmptyRemoval);
        final MergeThingStrategy underTestWithEmptyRemoval = new MergeThingStrategy(systemWithEmptyRemoval);

        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();

        final Thing existing = THING_V2.toBuilder()
                .setRevision(NEXT_REVISION - 1)
                .setFeature("sensor", ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("value1", 100)
                        .set("value2", 200)
                        .set("value3", 300)
                        .build())
                .build();

        final JsonObject mergePayload = JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("sensor", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("value1", JsonFactory.nullLiteral())
                                        .set("value2", 250)
                                        .set("value3", 350)
                                        .build())
                                .build())
                        .build())
                .build();

        final JsonObject patchConditions = JsonObject.newBuilder()
                .set("features/sensor/properties/value1", "lt(features/sensor/properties/value1,150)")
                .set("features/sensor/properties/value2", "lt(features/sensor/properties/value2,150)")
                .set("features/sensor/properties/value3", "lt(features/sensor/properties/value3,150)")
                .build();

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(UUID.randomUUID().toString())
                .putHeader(DittoHeaderDefinition.MERGE_THING_PATCH_CONDITIONS.getKey(), patchConditions.toString())
                .build();

        final MergeThing mergeThing = MergeThing.of(thingId, JsonPointer.of("/"), mergePayload, dittoHeaders);
        final MergeThingResponse expectedCommandResponse =
                ETagTestUtils.mergeThingResponse(existing, JsonPointer.of("/"), mergeThing.getDittoHeaders());

        final ThingMerged event = assertStagedModificationResult(underTestWithEmptyRemoval, existing, mergeThing,
                ThingMerged.class, expectedCommandResponse);

        final JsonValue mergedValue = event.getValue();
        assertThat(mergedValue.isObject()).isTrue();
        final JsonObject mergedObject = mergedValue.asObject();

        // Verify literal null is preserved (condition passed: 100 < 150)
        assertThat(mergedObject.getValue("features/sensor/properties/value1")).contains(JsonFactory.nullLiteral());
        // Verify value2 update was filtered out (condition failed: 200 >= 150)
        assertThat(mergedObject.getValue("features/sensor/properties/value2")).isEmpty();
        // Verify value3 update was filtered out (condition failed: 300 >= 150)
        assertThat(mergedObject.getValue("features/sensor/properties/value3")).isEmpty();
        // Verify the feature structure remains (not removed as empty) because it contains a literal null
        assertThat(mergedObject.contains("features")).isTrue();
        assertThat(mergedObject.getValue("features").orElseThrow().asObject().contains("sensor")).isTrue();

        systemWithEmptyRemoval.terminate();
    }

    @Test
    public void mergeThingWithAllPatchConditionsFailingResultsInNoEventEmission() {
        final Config configWithEmptyRemoval = ConfigFactory.parseString(
                "ditto.things.thing.merge.remove-empty-objects-after-patch-condition-filtering = true"
        ).withFallback(ConfigFactory.load("test"));
        final ActorSystem systemWithEmptyRemoval = ActorSystem.create("test-no-event", configWithEmptyRemoval);
        final MergeThingStrategy underTestWithEmptyRemoval = new MergeThingStrategy(systemWithEmptyRemoval);

        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();

        final Thing existing = THING_V2.toBuilder()
                .setRevision(NEXT_REVISION - 1)
                .setFeature("sensor", ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("temperature", 25)
                        .set("humidity", 60)
                        .set("pressure", 100)
                        .build())
                .build();

        final JsonObject mergePayload = JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("sensor", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("temperature", 30)
                                        .set("humidity", 70)
                                        .set("pressure", 200)
                                        .build())
                                .build())
                        .build())
                .build();

        final JsonObject patchConditions = JsonObject.newBuilder()
                .set("features/sensor/properties/temperature", "lt(features/sensor/properties/temperature,20)")
                .set("features/sensor/properties/humidity", "lt(features/sensor/properties/humidity,50)")
                .set("features/sensor/properties/pressure", "lt(features/sensor/properties/pressure,50)")
                .build();

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(UUID.randomUUID().toString())
                .putHeader(DittoHeaderDefinition.MERGE_THING_PATCH_CONDITIONS.getKey(), patchConditions.toString())
                .build();

        final MergeThing mergeThing = MergeThing.of(thingId, JsonPointer.of("/"), mergePayload, dittoHeaders);
        final Result<ThingEvent<?>> result = underTestWithEmptyRemoval.apply(context, existing, NEXT_REVISION, mergeThing);

        final ResultVisitor<ThingEvent<?>> mock = mock(Dummy.class);
        result.accept(mock, null);

        // Verify this is a query result (no mutation/event emission)
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<CompletionStage<WithDittoHeaders>> responseCaptor = 
                (ArgumentCaptor<CompletionStage<WithDittoHeaders>>) (Object) ArgumentCaptor.forClass(CompletionStage.class);
        verify(mock).onStagedQuery(any(MergeThing.class), responseCaptor.capture(), eq(null));

        // Verify response is MergeThingResponse (not an error)
        final WithDittoHeaders response = responseCaptor.getValue().toCompletableFuture().join();
        assertThat(response).isInstanceOf(MergeThingResponse.class);
        // Verify revision did NOT increment (no persistence occurred)
        assertThat(response.getDittoHeaders().get("etag")).contains("\"rev:" + (NEXT_REVISION - 1) + "\"");

        systemWithEmptyRemoval.terminate();
    }

    @Test
    public void mergeThingWithAllPatchConditionsFailingPersistsEmptyObjectWhenFlagDisabled() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();

        final Thing existing = THING_V2.toBuilder()
                .setRevision(NEXT_REVISION - 1)
                .setFeature("sensor", ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("temperature", 25)
                        .set("humidity", 60)
                        .build())
                .build();

        final JsonObject mergePayload = JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("sensor", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("temperature", 30)
                                        .set("humidity", 70)
                                        .build())
                                .build())
                        .build())
                .build();

        final JsonObject patchConditions = JsonObject.newBuilder()
                .set("features/sensor/properties/temperature", "lt(features/sensor/properties/temperature,20)")
                .set("features/sensor/properties/humidity", "lt(features/sensor/properties/humidity,50)")
                .build();

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(UUID.randomUUID().toString())
                .putHeader(DittoHeaderDefinition.MERGE_THING_PATCH_CONDITIONS.getKey(), patchConditions.toString())
                .build();

        final MergeThing mergeThing = MergeThing.of(thingId, JsonPointer.of("/"), mergePayload, dittoHeaders);
        final MergeThingResponse expectedCommandResponse =
                ETagTestUtils.mergeThingResponse(existing, JsonPointer.of("/"), mergeThing.getDittoHeaders());

        // With flag disabled (default), empty object should still be persisted (event is emitted)
        final ThingMerged event = assertStagedModificationResult(underTest, existing, mergeThing,
                ThingMerged.class, expectedCommandResponse);

        // Verify event was emitted (mutation occurred)
        assertThat(event).isNotNull();
        // Verify revision DID increment (persistence occurred)
        assertThat(event.getRevision()).isEqualTo(NEXT_REVISION);
    }


}
