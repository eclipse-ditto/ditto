/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.things.model.TestConstants.Thing.LOCATION_ATTRIBUTE;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionMigrated;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.eclipse.ditto.wot.api.generator.WotThingSkeletonGenerator;
import org.eclipse.ditto.wot.api.validator.WotThingModelValidator;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Unit test for {@link MigrateThingDefinitionStrategy}, verifying correct behavior using mocked Wot integration.
 */
public final class MigrateThingDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private static final String TEST_THING_DEFINITION_URL = "https://mock-model-1.0.0.tm.jsonld";

    private MigrateThingDefinitionStrategy underTest;
    private final WotThingSkeletonGenerator skeletonGeneratorMock = Mockito.mock(WotThingSkeletonGenerator.class);
    private final WotThingModelValidator modelValidatorMock = Mockito.mock(WotThingModelValidator.class);

    @Before
    public void setUp() throws Exception {
        final ActorSystem actorSystem = ActorSystem.create("MigrateThingDefinitionStrategyTest", ConfigFactory.load("test"));
        underTest = new MigrateThingDefinitionStrategy(actorSystem);
        injectPrivateField(underTest, "wotThingSkeletonGenerator", skeletonGeneratorMock);
        injectPrivateField(underTest, "wotThingModelValidator", modelValidatorMock);
    }

    // --- Baseline (no placeholders) ---

    @Test
    public void migrateExistingThing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final Thing existingThing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();
        final Thing skeletonThing = ThingsModelFactory.newThing(getExpectedThingJson());
        stubWotInteractions(skeletonThing);

        final MigrateThingDefinition command = MigrateThingDefinition.of(
                context.getState(),
                TEST_THING_DEFINITION_URL,
                JsonObject.empty(),
                Collections.emptyMap(),
                true,
                DittoHeaders.empty()
        );

        final MigrateThingDefinitionResponse expectedResponse = ETagTestUtils.migrateThingDefinitionResponse(
                context.getState(),
                getExpectedThingJson(),
                getExpectedMergedThing(),
                command.getDittoHeaders()
        );

        assertStagedModificationResult(underTest, existingThing, command, ThingDefinitionMigrated.class, expectedResponse);
    }

    @Test
    public void migrateExistingThingPreservesNullValuesInMigrationPayload() throws Exception {
        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("on", JsonFactory.nullLiteral())
                        .build())
                .build();
        runMigrationAndAssertPatch(migrationPayload, migrationPayload);
    }


    @Test
    public void migrateExistingThingThingJsonPlaceholderFieldRenaming() throws Exception {
        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("manufacturer", "{{ thing-json:attributes/maker }}")
                        .set("locationCopy", "{{ thing-json:attributes/location }}")
                        .set("latitude", "{{ thing-json:attributes/location/latitude }}")
                        .build())
                .build();
        final JsonObject resolvedPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("manufacturer", "Bosch")
                        .set("locationCopy", LOCATION_ATTRIBUTE)
                        .set("latitude", LOCATION_ATTRIBUTE.getValue("latitude").orElseThrow())
                        .build())
                .build();
        runMigrationAndAssertPatch(migrationPayload, resolvedPayload);
    }

    @Test
    public void migrateExistingThingThingJsonPlaceholderDataRestructuring() throws Exception {
        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("location", "{{ thing-json:attributes/location }}")
                                .build())
                        .build())
                .build();
        final JsonObject resolvedPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("location", LOCATION_ATTRIBUTE)
                                .build())
                        .build())
                .build();
        runMigrationAndAssertPatch(migrationPayload, resolvedPayload);
    }

    @Test
    public void migrateExistingThingThingJsonPlaceholderFieldConsolidation() throws Exception {
        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("deviceInfo", JsonFactory.newObjectBuilder()
                                .set("maker", "{{ thing-json:attributes/maker }}")
                                .set("location", "{{ thing-json:attributes/location }}")
                                .build())
                        .build())
                .build();
        final JsonObject resolvedPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("deviceInfo", JsonFactory.newObjectBuilder()
                                .set("maker", "Bosch")
                                .set("location", LOCATION_ATTRIBUTE)
                                .build())
                        .build())
                .build();
        runMigrationAndAssertPatch(migrationPayload, resolvedPayload);
    }

    @Test
    public void migrateExistingThingThingJsonPlaceholderConditionalWithPatchConditions() throws Exception {
        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("manufacturer", "{{ thing-json:attributes/maker }}")
                        .set("optional", "{{ thing-json:attributes/does-not-exist }}")
                        .build())
                .build();
        final var patchConditions = Collections.singletonMap(
                ResourceKey.newInstance("thing:/attributes/optional"),
                "eq(attributes/maker,\"NotBosch\")"
        );
        final JsonObject resolvedPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("manufacturer", "Bosch")
                        .build())
                .build();
        runMigrationWithPatchConditionsAndAssertPatch(migrationPayload, patchConditions, resolvedPayload);
    }

    @Test
    public void migrateExistingThingThingJsonPlaceholderTypePreservation() throws Exception {
        final Thing existingThing = THING_V2.toBuilder()
                .setAttributes(THING_V2.getAttributes()
                        .orElseGet(ThingsModelFactory::emptyAttributes)
                        .toBuilder()
                        .set("tags", JsonFactory.newArrayBuilder()
                                .add("building-simulator")
                                .add("DO_NOT_DELETE")
                                .build())
                        .build())
                .setRevision(NEXT_REVISION - 1)
                .build();
        final Thing skeletonThing = ThingsModelFactory.newThing(getExpectedThingJson());
        stubWotInteractions(skeletonThing);

        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("productKey", "PK={{ thing-json:attributes/maker }}")
                        .set("tags", "{{ thing-json:attributes/tags }}")
                        .build())
                .build();
        final JsonObject resolvedPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("productKey", "PK=Bosch")
                        .set("tags", JsonFactory.newArrayBuilder()
                                .add("building-simulator")
                                .add("DO_NOT_DELETE")
                                .build())
                        .build())
                .build();

        runMigrationAndAssertPatch(existingThing, skeletonThing, migrationPayload, resolvedPayload);
    }

    @Test
    public void migrateExistingThingThingJsonPlaceholderLegacyFormatTypePreservation() throws Exception {
        final Thing existingThing = THING_V2.toBuilder()
                .setAttributes(THING_V2.getAttributes()
                        .orElseGet(ThingsModelFactory::emptyAttributes)
                        .toBuilder()
                        .set("tags", JsonFactory.newArrayBuilder()
                                .add("a")
                                .add("b")
                                .build())
                        .build())
                .setRevision(NEXT_REVISION - 1)
                .build();
        final Thing skeletonThing = ThingsModelFactory.newThing(getExpectedThingJson());
        stubWotInteractions(skeletonThing);

        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("manufacturer", "${ thing-json:attributes/maker }")
                        .set("tags", "${ thing-json:attributes/tags }")
                        .build())
                .build();
        final JsonObject resolvedPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("manufacturer", "Bosch")
                        .set("tags", JsonFactory.newArrayBuilder()
                                .add("a")
                                .add("b")
                                .build())
                        .build())
                .build();

        runMigrationAndAssertPatch(existingThing, skeletonThing, migrationPayload, resolvedPayload);
    }

    @Test
    public void migrateExistingThingThingJsonPlaceholderTypePreservationBooleanAndNumber() throws Exception {
        final Thing existingThing = THING_V2.toBuilder()
                .setAttributes(THING_V2.getAttributes()
                        .orElseGet(ThingsModelFactory::emptyAttributes)
                        .toBuilder()
                        .set("isOn", JsonValue.of(true))
                        .set("level", JsonValue.of(0.75))
                        .build())
                .setRevision(NEXT_REVISION - 1)
                .build();
        final Thing skeletonThing = ThingsModelFactory.newThing(getExpectedThingJson());
        stubWotInteractions(skeletonThing);

        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("powerOn", "{{ thing-json:attributes/isOn }}")
                        .set("intensity", "{{ thing-json:attributes/level }}")
                        .build())
                .build();
        final JsonObject resolvedPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("powerOn", true)
                        .set("intensity", 0.75)
                        .build())
                .build();

        runMigrationAndAssertPatch(existingThing, skeletonThing, migrationPayload, resolvedPayload);
    }

    @Test
    public void migrateExistingThingThingJsonPlaceholderPipelineFallbackYieldsString() throws Exception {
        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("manufacturerUpper", "{{ thing-json:attributes/maker | fn:upper() }}")
                        .build())
                .build();
        final JsonObject resolvedPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("manufacturerUpper", "BOSCH")
                        .build())
                .build();
        runMigrationAndAssertPatch(migrationPayload, resolvedPayload);
    }

    @Test
    public void migrateExistingThingThingJsonPlaceholderMultipleInOneStringYieldsString() throws Exception {
        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("composite", "{{ thing-json:attributes/maker }}:{{ thing-json:attributes/maker }}")
                        .build())
                .build();
        final JsonObject resolvedPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("composite", "Bosch:Bosch")
                        .build())
                .build();
        runMigrationAndAssertPatch(migrationPayload, resolvedPayload);
    }

    @Test
    public void migrateExistingThingFailsWhenThingJsonPlaceholderPathMissing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final Thing existingThing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();
        final Thing skeletonThing = ThingsModelFactory.newThing(getExpectedThingJson());
        stubWotInteractions(skeletonThing);

        final DittoHeaders dittoHeaders = provideHeaders(context);
        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("missing", "{{ thing-json:attributes/does-not-exist }}")
                        .build())
                .build();

        final MigrateThingDefinition command = MigrateThingDefinition.of(
                context.getState(),
                TEST_THING_DEFINITION_URL,
                migrationPayload,
                Collections.emptyMap(),
                true,
                dittoHeaders
        );

        final UnresolvedPlaceholderException expected = UnresolvedPlaceholderException.newBuilder(
                "{{ thing-json:attributes/does-not-exist }}")
                .dittoHeaders(dittoHeaders)
                .build();

        assertErrorResult(underTest, existingThing, command, expected);
    }


    private void runMigrationAndAssertPatch(final JsonObject migrationPayload,
            final JsonObject resolvedPayload) throws Exception {
        final Thing existingThing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();
        final Thing skeletonThing = ThingsModelFactory.newThing(getExpectedThingJson());
        runMigrationAndAssertPatch(existingThing, skeletonThing, migrationPayload, resolvedPayload);
    }

    private void runMigrationAndAssertPatch(final Thing existingThing,
            final Thing skeletonThing,
            final JsonObject migrationPayload,
            final JsonObject resolvedPayload) throws Exception {
        stubWotInteractions(skeletonThing);
        final CommandStrategy.Context<ThingId> context = getDefaultContext();

        final MigrateThingDefinition command = MigrateThingDefinition.of(
                context.getState(),
                TEST_THING_DEFINITION_URL,
                migrationPayload,
                Collections.emptyMap(),
                true,
                DittoHeaders.empty()
        );

        final JsonObject expectedPatch = JsonFactory.newObject(resolvedPayload, getExpectedThingJson());
        final Result<ThingEvent<?>> result = underTest.apply(context, existingThing, NEXT_REVISION, command);
        final MigrateThingDefinitionResponse actualResponse =
                assertStagedMutationAndGetResponse(result, ThingDefinitionMigrated.class);

        JSONAssert.assertEquals(expectedPatch.toString(), actualResponse.getPatch().toString(), true);
        assertThat(actualResponse.getMergeStatus()).isEqualTo(MigrateThingDefinitionResponse.MergeStatus.APPLIED);
    }

    private void runMigrationWithPatchConditionsAndAssertPatch(final JsonObject migrationPayload,
            final java.util.Map<ResourceKey, String> patchConditions,
            final JsonObject resolvedPayload) throws Exception {
        final Thing existingThing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();
        final Thing skeletonThing = ThingsModelFactory.newThing(getExpectedThingJson());
        stubWotInteractions(skeletonThing);
        final CommandStrategy.Context<ThingId> context = getDefaultContext();

        final MigrateThingDefinition command = MigrateThingDefinition.of(
                context.getState(),
                TEST_THING_DEFINITION_URL,
                migrationPayload,
                patchConditions,
                true,
                DittoHeaders.empty()
        );

        final JsonObject expectedPatch = JsonFactory.newObject(resolvedPayload, getExpectedThingJson());
        final Result<ThingEvent<?>> result = underTest.apply(context, existingThing, NEXT_REVISION, command);
        final MigrateThingDefinitionResponse actualResponse =
                assertStagedMutationAndGetResponse(result, ThingDefinitionMigrated.class);

        JSONAssert.assertEquals(expectedPatch.toString(), actualResponse.getPatch().toString(), true);
        assertThat(actualResponse.getMergeStatus()).isEqualTo(MigrateThingDefinitionResponse.MergeStatus.APPLIED);
    }

    private static void injectPrivateField(final Object target, final String fieldName, final Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                final Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new IllegalArgumentException("Field '" + fieldName + "' not found in class hierarchy.");
    }

    private void stubWotInteractions(final Thing skeletonThing) {
        Mockito.when(skeletonGeneratorMock.provideThingSkeletonForCreation(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(skeletonThing)));
        Mockito.when(modelValidatorMock.validateThing(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(null));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static MigrateThingDefinitionResponse assertStagedMutationAndGetResponse(
            final Result<ThingEvent<?>> result,
            final Class<? extends ThingEvent<?>> expectedEventClass) {
        final ResultVisitor<ThingEvent<?>> mock = Mockito.mock(ResultVisitor.class);
        final ArgumentCaptor<CompletionStage> eventStageCaptor = ArgumentCaptor.forClass(CompletionStage.class);
        final ArgumentCaptor<CompletionStage> responseStageCaptor = ArgumentCaptor.forClass(CompletionStage.class);

        result.accept(mock, null);

        Mockito.verify(mock).onStagedMutation(Mockito.any(), eventStageCaptor.capture(), responseStageCaptor.capture(),
                Mockito.anyBoolean(), Mockito.eq(false), Mockito.eq(null));

        final ThingEvent<?> event = (ThingEvent<?>) eventStageCaptor.getValue().toCompletableFuture().join();
        assertThat(event).isInstanceOf(expectedEventClass);

        return (MigrateThingDefinitionResponse) responseStageCaptor.getValue().toCompletableFuture().join();
    }

    private static JsonObject getExpectedThingJson() {
        return JsonFactory.newObjectBuilder()
                .set("definition", TEST_THING_DEFINITION_URL)
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("on", false)
                        .set("color", JsonFactory.newObjectBuilder()
                                .set("r", 0)
                                .set("g", 0)
                                .set("b", 0)
                                .build())
                        .set("dimmer-level", 0.0)
                        .build())
                .build();
    }

    private Thing getExpectedMergedThing() {
        return ThingsModelFactory.newThingBuilder(getExpectedThingJson())
                .setRevision(ThingRevision.newInstance(NEXT_REVISION))
                .build();
    }
}
