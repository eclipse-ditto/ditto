/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.api.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.wot.api.config.DefaultTmValidationConfig;
import org.eclipse.ditto.wot.api.resolver.ThingSubmodel;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.model.AtContext;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.TmOptional;
import org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException;
import org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests the validation against alternative thing definitions of {@link DefaultWotThingModelValidator}.
 */
public final class DefaultWotThingModelValidatorAlternativesTest {

    private static final String BASE_URL = "https://models.example.org/gateway/";
    private static final String V8 = BASE_URL + "gateway-8.0.0.tm.jsonld";
    private static final String V9 = BASE_URL + "gateway-9.0.0.tm.jsonld";
    private static final String V91 = BASE_URL + "gateway-9.1.0.tm.jsonld";
    private static final String SENSOR_V8 = BASE_URL + "sensor-8.0.0.tm.jsonld";
    private static final String SENSOR_V9 = BASE_URL + "sensor-9.0.0.tm.jsonld";
    private static final String UNKNOWN = BASE_URL + "unknown-1.0.0.tm.jsonld";

    private static final String FEATURE_ID = "sensor";
    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto:gateway");
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId("alternatives-test")
            .build();

    private static final ThingModel SENSOR_V8_TM = thingModel(Map.of("temp", SingleDataSchema.newIntegerSchemaBuilder()
            .build()));
    private static final ThingModel SENSOR_V9_TM =
            thingModel(Map.of("temperature", SingleDataSchema.newNumberSchemaBuilder().build()));
    private static final ThingModel V8_TM = thingModel(Map.of("a", SingleDataSchema.newIntegerSchemaBuilder()
            .build()));
    private static final ThingModel V9_TM = thingModel(Map.of("b", SingleDataSchema.newStringSchemaBuilder()
            .build()));
    private static final ThingModel V91_TM = thingModel(Map.of("c", SingleDataSchema.newBooleanSchemaBuilder()
            .build()));

    private static final WotThingModelResolver RESOLVER = mock(WotThingModelResolver.class);

    private static DefaultWotThingModelValidator underTest;

    @BeforeClass
    public static void createValidator() {
        underTest = DefaultWotThingModelValidator.getInstance(RESOLVER, Runnable::run, config(""));
    }

    @Before
    public void setUp() throws Exception {
        reset(RESOLVER);
        stubModel(V8, V8_TM, Map.of(new ThingSubmodel(FEATURE_ID, IRI.of(SENSOR_V8)), SENSOR_V8_TM));
        stubModel(V9, V9_TM, Map.of(new ThingSubmodel(FEATURE_ID, IRI.of(SENSOR_V9)), SENSOR_V9_TM));
        stubModel(V91, V91_TM, Map.of());
        stubModel(SENSOR_V8, SENSOR_V8_TM, Map.of());
        stubModel(SENSOR_V9, SENSOR_V9_TM, Map.of());
        when(RESOLVER.resolveThingModel(eq(new URL(UNKNOWN)), any())).thenReturn(CompletableFuture.failedFuture(
                WotThingModelNotAccessibleException.newBuilder(new URL(UNKNOWN)).build()));
    }

    @Test
    public void writeIsRejectedWithoutAlternatives() {
        underTest.updateConfig(config(""));

        assertFailsWithPayloadValidationException(
                underTest.validateThingAttributes(definition(V8), attributes("b", "new-shape"), attributesPath(),
                        DITTO_HEADERS));
    }

    @Test
    public void writeIsAcceptedWhenAlternativePasses() {
        underTest.updateConfig(config(section(V8, V9)));

        assertSucceeds(underTest.validateThingAttributes(definition(V8), attributes("b", "new-shape"),
                attributesPath(), DITTO_HEADERS));
    }

    @Test
    public void writePassingThePrimaryModelDoesNotResolveAlternatives() throws Exception {
        underTest.updateConfig(config(section(V8, V9)));

        assertSucceeds(underTest.validateThingAttributes(definition(V8), attributes("a", 42), attributesPath(),
                DITTO_HEADERS));
        verify(RESOLVER, never()).resolveThingModel(eq(new URL(V9)), any());
    }

    @Test
    public void writeFailingAllModelsIsRejectedWithThePrimaryError() {
        underTest.updateConfig(config(""));
        final Throwable primaryError = failureOf(underTest.validateThingAttributes(definition(V8),
                attributes("b", 42), attributesPath(), DITTO_HEADERS));

        underTest.updateConfig(config(section(V8, V9)));
        final Throwable error = failureOf(underTest.validateThingAttributes(definition(V8),
                attributes("b", 42), attributesPath(), DITTO_HEADERS));

        assertThat(error).isInstanceOf(WotThingModelPayloadValidationException.class);
        assertThat(((WotThingModelPayloadValidationException) error).toJson())
                .isEqualTo(((WotThingModelPayloadValidationException) primaryError).toJson());
    }

    @Test
    public void alternativesAreNotChained() throws Exception {
        underTest.updateConfig(config(section(V8, V9) + "," + section(V9, V91)));

        assertFailsWithPayloadValidationException(
                underTest.validateThingAttributes(definition(V8), attributes("c", true), attributesPath(),
                        DITTO_HEADERS));
        verify(RESOLVER, never()).resolveThingModel(eq(new URL(V91)), any());
    }

    @Test
    public void migratedThingRejectsOldShapeWithoutReverseEntry() {
        underTest.updateConfig(config(section(V8, V9)));

        assertFailsWithPayloadValidationException(
                underTest.validateThingAttributes(definition(V9), attributes("a", 42), attributesPath(),
                        DITTO_HEADERS));
    }

    @Test
    public void migratedThingAcceptsOldShapeWithReverseEntry() {
        underTest.updateConfig(config(section(V8, V9) + "," + section(V9, V8)));

        assertSucceeds(underTest.validateThingAttributes(definition(V9), attributes("a", 42), attributesPath(),
                DITTO_HEADERS));
    }

    @Test
    public void overlappingSectionsCombineTheirAlternatives() {
        underTest.updateConfig(config(section(V8, V9) + "," +
                section("^" + BASE_URL.replace(".", "\\\\.") + "gateway-8\\\\..*$", V91)));

        assertSucceeds(underTest.validateThingAttributes(definition(V8), attributes("b", "new-shape"),
                attributesPath(), DITTO_HEADERS));
        assertSucceeds(underTest.validateThingAttributes(definition(V8), attributes("c", true),
                attributesPath(), DITTO_HEADERS));
    }

    @Test
    public void modelResolutionFailureIsNotRetried() throws Exception {
        underTest.updateConfig(config(section(UNKNOWN, V9)));

        final Throwable error = failureOf(underTest.validateThingAttributes(definition(UNKNOWN),
                attributes("b", "new-shape"), attributesPath(), DITTO_HEADERS));

        assertThat(error).isInstanceOf(WotThingModelNotAccessibleException.class);
        verify(RESOLVER, never()).resolveThingModel(eq(new URL(V9)), any());
    }

    @Test
    public void unresolvableAlternativeIsSkipped() {
        underTest.updateConfig(config(section(V8, UNKNOWN, V9)));

        assertSucceeds(underTest.validateThingAttributes(definition(V8), attributes("b", "new-shape"),
                attributesPath(), DITTO_HEADERS));
    }

    @Test
    public void thingAttributeWriteIsAcceptedWhenAlternativePasses() {
        underTest.updateConfig(config(section(V8, V9)));

        assertSucceeds(underTest.validateThingAttribute(definition(V8), JsonPointer.of("b"),
                JsonValue.of("new-shape"), attributesPath().append(JsonPointer.of("b")), DITTO_HEADERS));
    }

    @Test
    public void featurePropertyWriteIsValidatedAgainstTheAlternativeSubmodel() {
        underTest.updateConfig(config(""));
        assertFailsWithPayloadValidationException(writeSensorProperty("temperature", JsonValue.of(21.5)));

        underTest.updateConfig(config(section(V8, V9)));
        assertSucceeds(writeSensorProperty("temperature", JsonValue.of(21.5)));
        assertFailsWithPayloadValidationException(writeSensorProperty("temperature", JsonValue.of("warm")));
    }

    @Test
    public void featureWriteIsRejectedWhenTheFeatureIsNotModeledInTheAlternative() {
        underTest.updateConfig(config(section(V8, V9)));

        assertFailsWithPayloadValidationException(underTest.validateFeatureProperty(definition(V8),
                FeatureDefinition.fromIdentifier(SENSOR_V8), "other", JsonPointer.of("temperature"),
                JsonValue.of(21.5), false, featurePath("other").append(JsonPointer.of("properties/temperature")),
                DITTO_HEADERS));
    }

    @Test
    public void featurePropertiesWriteIsValidatedAgainstTheAlternativeSubmodel() {
        underTest.updateConfig(config(section(V8, V9)));

        assertSucceeds(underTest.validateFeatureProperties(definition(V8),
                FeatureDefinition.fromIdentifier(SENSOR_V8), FEATURE_ID,
                FeatureProperties.newBuilder().set("temperature", 21.5).build(), false,
                featurePath(FEATURE_ID).append(JsonPointer.of("properties")), DITTO_HEADERS));
    }

    @Test
    public void thingScopedDeletionIsAcceptedWhenAlternativeAllowsIt() {
        underTest.updateConfig(config(""));
        assertFailsWithPayloadValidationException(underTest.validateThingScopedDeletion(definition(V8),
                attributesPath().append(JsonPointer.of("a")), DITTO_HEADERS));

        underTest.updateConfig(config(section(V8, V9)));
        assertSucceeds(underTest.validateThingScopedDeletion(definition(V8),
                attributesPath().append(JsonPointer.of("a")), DITTO_HEADERS));
    }

    @Test
    public void featureScopedDeletionIsAcceptedWhenAlternativeAllowsIt() {
        final JsonPointer path = featurePath(FEATURE_ID).append(JsonPointer.of("properties/temp"));
        underTest.updateConfig(config(""));
        assertFailsWithPayloadValidationException(underTest.validateFeatureScopedDeletion(definition(V8),
                FeatureDefinition.fromIdentifier(SENSOR_V8), FEATURE_ID, path, DITTO_HEADERS));

        underTest.updateConfig(config(section(V8, V9)));
        assertSucceeds(underTest.validateFeatureScopedDeletion(definition(V8),
                FeatureDefinition.fromIdentifier(SENSOR_V8), FEATURE_ID, path, DITTO_HEADERS));
    }

    @Test
    public void mergeValidatesOnlyTheChangedAttributesAgainstTheAlternative() {
        final Thing mergedThing = thing(Attributes.newBuilder().set("a", 42).set("b", "new-shape").build(),
                FeatureProperties.newBuilder().set("temp", 20).build());
        final MergeThing mergeThing = MergeThing.of(THING_ID, JsonPointer.empty(),
                JsonObject.newBuilder().set("attributes", JsonObject.newBuilder().set("b", "new-shape").build())
                        .build(), DITTO_HEADERS);

        underTest.updateConfig(config(""));
        assertFailsWithPayloadValidationException(
                underTest.validateMergeThing(definition(V8), mergeThing, mergedThing, JsonPointer.empty(),
                        DITTO_HEADERS));

        underTest.updateConfig(config(section(V8, V9)));
        assertSucceeds(underTest.validateMergeThing(definition(V8), mergeThing, mergedThing, JsonPointer.empty(),
                DITTO_HEADERS));
    }

    @Test
    public void mergeWithInvalidChangedAttributeIsRejected() {
        final Thing mergedThing = thing(Attributes.newBuilder().set("a", 42).set("b", 13).build(),
                FeatureProperties.newBuilder().set("temp", 20).build());
        final MergeThing mergeThing = MergeThing.of(THING_ID, JsonPointer.of("attributes/b"), JsonValue.of(13),
                DITTO_HEADERS);
        underTest.updateConfig(config(section(V8, V9)));

        assertFailsWithPayloadValidationException(
                underTest.validateMergeThing(definition(V8), mergeThing, mergedThing, JsonPointer.empty(),
                        DITTO_HEADERS));
    }

    @Test
    public void mergeValidatesOnlyTheChangedFeaturePropertiesAgainstTheAlternative() {
        final Thing mergedThing = thing(Attributes.newBuilder().set("a", 42).build(),
                FeatureProperties.newBuilder().set("temp", 20).set("temperature", 21.5).build());
        final MergeThing mergeThing = MergeThing.of(THING_ID,
                featurePath(FEATURE_ID).append(JsonPointer.of("properties/temperature")), JsonValue.of(21.5),
                DITTO_HEADERS);

        underTest.updateConfig(config(""));
        assertFailsWithPayloadValidationException(
                underTest.validateMergeThing(definition(V8), mergeThing, mergedThing, JsonPointer.empty(),
                        DITTO_HEADERS));

        underTest.updateConfig(config(section(V8, V9)));
        assertSucceeds(underTest.validateMergeThing(definition(V8), mergeThing, mergedThing, JsonPointer.empty(),
                DITTO_HEADERS));
    }

    @Test
    public void mergeDeletingAPrimaryRequiredAttributeIsAcceptedWhenAlternativeAllowsIt() {
        final Thing mergedThing = thing(Attributes.newBuilder().set("b", "new-shape").build(),
                FeatureProperties.newBuilder().set("temp", 20).build());
        final MergeThing mergeThing = MergeThing.of(THING_ID, JsonPointer.empty(),
                JsonObject.newBuilder().set("attributes", JsonObject.newBuilder()
                        .set("a", JsonValue.nullLiteral())
                        .set("b", "new-shape")
                        .build()).build(), DITTO_HEADERS);
        underTest.updateConfig(config(section(V8, V9)));

        assertSucceeds(underTest.validateMergeThing(definition(V8), mergeThing, mergedThing, JsonPointer.empty(),
                DITTO_HEADERS));
    }

    private CompletionStage<Void> writeSensorProperty(final String property, final JsonValue value) {
        return underTest.validateFeatureProperty(definition(V8), FeatureDefinition.fromIdentifier(SENSOR_V8),
                FEATURE_ID, JsonPointer.of(property), value, false,
                featurePath(FEATURE_ID).append(JsonPointer.of("properties/" + property)), DITTO_HEADERS);
    }

    private static void stubModel(final String url, final ThingModel thingModel,
            final Map<ThingSubmodel, ThingModel> subModels) throws Exception {
        when(RESOLVER.resolveThingModel(eq(new URL(url)), any()))
                .thenReturn(CompletableFuture.completedFuture(thingModel));
        when(RESOLVER.resolveThingModelSubmodels(eq(thingModel), any()))
                .thenReturn(CompletableFuture.completedFuture(subModels));
    }

    private static ThingModel thingModel(final Map<String, SingleDataSchema> requiredProperties) {
        return ThingModel.newBuilder()
                .setAtContext(AtContext.newSingleUriAtContext("https://www.w3.org/2022/wot/td/v1.1"))
                .setProperties(Properties.from(requiredProperties.entrySet().stream()
                        .map(entry -> Property.newBuilder(entry.getKey()).setSchema(entry.getValue()).build())
                        .toList()))
                .setTmOptional(TmOptional.of(List.of()))
                .build();
    }

    private static Thing thing(final Attributes attributes, final FeatureProperties sensorProperties) {
        return Thing.newBuilder()
                .setId(THING_ID)
                .setDefinition(definition(V8))
                .setAttributes(attributes)
                .setFeature(Feature.newBuilder()
                        .definition(FeatureDefinition.fromIdentifier(SENSOR_V8))
                        .properties(sensorProperties)
                        .withId(FEATURE_ID)
                        .build())
                .build();
    }

    private static DefaultTmValidationConfig config(final String dynamicConfiguration) {
        return DefaultTmValidationConfig.of(ConfigFactory.parseString(
                "tm-model-validation {\n" +
                        "  enabled = true\n" +
                        "  enforce-context-prefixes = false\n" +
                        "  dynamic-configuration = [" + dynamicConfiguration + "]\n" +
                        "}"));
    }

    private static String section(final String thingDefinitionPattern, final String... alternatives) {
        final String pattern = thingDefinitionPattern.startsWith("^") ? thingDefinitionPattern :
                "^" + thingDefinitionPattern.replace(".", "\\\\.") + "$";
        return "{\n" +
                "  validation-context.thing-definition-patterns = [\"" + pattern + "\"]\n" +
                "  config-overrides.alternative-thing-definitions = [" +
                String.join(",", List.of(alternatives).stream().map(a -> "\"" + a + "\"").toList()) + "]\n" +
                "}";
    }

    private static ThingDefinition definition(final String url) {
        return ThingsModelFactory.newDefinition(url);
    }

    private static Attributes attributes(final String key, final Object value) {
        return Attributes.newBuilder().set(key, JsonValue.of(value)).build();
    }

    private static JsonPointer attributesPath() {
        return Thing.JsonFields.ATTRIBUTES.getPointer();
    }

    private static JsonPointer featurePath(final String featureId) {
        return JsonPointer.of("features/" + featureId);
    }

    private static void assertSucceeds(final CompletionStage<Void> validation) {
        assertThat(failureOf(validation)).isNull();
    }

    private static void assertFailsWithPayloadValidationException(final CompletionStage<Void> validation) {
        assertThat(failureOf(validation)).isInstanceOf(WotThingModelPayloadValidationException.class);
    }

    @Nullable
    private static Throwable failureOf(final CompletionStage<Void> validation) {
        try {
            validation.toCompletableFuture().get(5, TimeUnit.SECONDS);
            return null;
        } catch (final ExecutionException e) {
            return e.getCause();
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

}
