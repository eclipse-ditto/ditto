/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.wot.model.Action;
import org.eclipse.ditto.wot.model.Actions;
import org.eclipse.ditto.wot.model.AtContext;
import org.eclipse.ditto.wot.model.BaseLink;
import org.eclipse.ditto.wot.model.Event;
import org.eclipse.ditto.wot.model.Events;
import org.eclipse.ditto.wot.model.Links;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.SingleAtContext;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.SingleUriAtContext;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.TmOptional;
import org.eclipse.ditto.wot.model.TmOptionalElement;
import org.eclipse.ditto.wot.validation.config.FeatureValidationConfig;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;
import org.junit.Before;
import org.junit.Test;

/**
 * Provides unit tests for testing the "feature" related functionality of {@link WotThingModelValidation}.
 */
public final class WotThingModelValidationFeatureLevelTest {

    private static final String DITTO_CONTEXT_PREFIX = "ditto";
    private static final String CATEGORY_CONFIG = "config";

    private static final String PROP_SOME_BOOL = "someBool";
    private static final String PROP_SOME_INT = "someInt";
    private static final JsonPointer PROP_PATH_SOME_INT = JsonPointer.of(CATEGORY_CONFIG + "/" + PROP_SOME_INT);
    private static final String PROP_SOME_NUMBER = "someNumber";
    private static final String PROP_SOME_STRING = "someString";
    private static final JsonPointer PROP_PATH_SOME_STRING = JsonPointer.of(CATEGORY_CONFIG + "/" + PROP_SOME_STRING);
    private static final String PROP_SOME_ARRAY_STRINGS = "someArray_strings";
    private static final String PROP_SOME_OBJECT = "someObject";

    private static final JsonObject PROP_KNOWN_SOME_OBJECT = JsonObject.newBuilder()
            .set(PROP_SOME_BOOL, false)
            .set(PROP_SOME_INT, 3)
            .set(PROP_SOME_STRING, "helo")
            .build();

    private static final Properties KNOWN_PROPERTIES = Properties.from(List.of(
            Property.newBuilder(PROP_SOME_BOOL)
                    .setSchema(SingleDataSchema.newBooleanSchemaBuilder().build())
                    .build(),
            Property.newBuilder(PROP_SOME_INT)
                    .setSchema(SingleDataSchema.newIntegerSchemaBuilder().build())
                    .set(DITTO_CONTEXT_PREFIX + ":category", CATEGORY_CONFIG)
                    .build(),
            Property.newBuilder(PROP_SOME_NUMBER)
                    .setSchema(SingleDataSchema.newNumberSchemaBuilder().build())
                    .build(),
            Property.newBuilder(PROP_SOME_STRING)
                    .setSchema(SingleDataSchema.newStringSchemaBuilder().build())
                    .set(DITTO_CONTEXT_PREFIX + ":category", CATEGORY_CONFIG)
                    .build(),
            Property.newBuilder(PROP_SOME_ARRAY_STRINGS)
                    .setSchema(SingleDataSchema.newArraySchemaBuilder()
                            .setItems(SingleDataSchema.newStringSchemaBuilder().build())
                            .build())
                    .build(),
            Property.newBuilder(PROP_SOME_OBJECT)
                    .setSchema(SingleDataSchema.newObjectSchemaBuilder()
                            .setProperties(Map.of(
                                    PROP_SOME_BOOL, SingleDataSchema.newBooleanSchemaBuilder().build(),
                                    PROP_SOME_INT, SingleDataSchema.newIntegerSchemaBuilder().build(),
                                    PROP_SOME_STRING, SingleDataSchema.newStringSchemaBuilder().build()
                            ))
                            .setRequired(List.of(PROP_SOME_BOOL, PROP_SOME_STRING))
                            .enhanceObjectBuilder(builder -> builder.set("additionalProperties", false))
                            .build())
                    .build()
    ));

    private static final String ACTION_PROCESS_BOOL = "processBool";
    private static final String ACTION_PROCESS_OBJECT = "processObject";

    private static final Actions KNOWN_ACTIONS = Actions.from(List.of(
            Action.newBuilder(ACTION_PROCESS_BOOL)
                    .setInput(SingleDataSchema.newBooleanSchemaBuilder().build())
                    .setOutput(SingleDataSchema.newBooleanSchemaBuilder().build())
                    .build(),
            Action.newBuilder(ACTION_PROCESS_OBJECT)
                    .setInput(SingleDataSchema.newObjectSchemaBuilder()
                            .setProperties(Map.of(
                                    PROP_SOME_BOOL, SingleDataSchema.newBooleanSchemaBuilder().build(),
                                    PROP_SOME_INT, SingleDataSchema.newIntegerSchemaBuilder().build(),
                                    PROP_SOME_STRING, SingleDataSchema.newStringSchemaBuilder().build()
                            ))
                            .setRequired(List.of(PROP_SOME_BOOL, PROP_SOME_STRING))
                            .build()
                    )
                    .setOutput(SingleDataSchema.newObjectSchemaBuilder()
                            .setProperties(Map.of(
                                    PROP_SOME_BOOL, SingleDataSchema.newBooleanSchemaBuilder().build(),
                                    PROP_SOME_INT, SingleDataSchema.newIntegerSchemaBuilder().build(),
                                    PROP_SOME_STRING, SingleDataSchema.newStringSchemaBuilder().build()
                            ))
                            .setRequired(List.of(PROP_SOME_BOOL, PROP_SOME_STRING))
                            .build())
                    .build()
    ));

    private static final String EVENT_EMIT_INT = "emitInt";
    private static final String EVENT_EMIT_ARRAY = "emitArray_objects";

    private static final Events KNOWN_EVENTS = Events.from(List.of(
            Event.newBuilder(EVENT_EMIT_INT)
                    .setData(SingleDataSchema.newIntegerSchemaBuilder().build())
                    .build(),
            Event.newBuilder(EVENT_EMIT_ARRAY)
                    .setData(SingleDataSchema.newArraySchemaBuilder()
                            .setItems(SingleDataSchema.newObjectSchemaBuilder()
                                    .setProperties(Map.of(
                                            PROP_SOME_BOOL, SingleDataSchema.newBooleanSchemaBuilder().build(),
                                            PROP_SOME_INT, SingleDataSchema.newIntegerSchemaBuilder().build(),
                                            PROP_SOME_STRING, SingleDataSchema.newStringSchemaBuilder().build()
                                    ))
                                    .setRequired(List.of(PROP_SOME_BOOL, PROP_SOME_STRING))
                                    .build())
                            .build()
                    )
                    .build()
    ));

    private static final String KNOWN_FEATURE_ID = "known-feature";
    private static final String KNOWN_FEATURE_ID_2 = "known-feature-2";

    private static final ThingModel KNOWN_THING_LEVEL_TM_WITH_SUBMODELS = ThingModel.newBuilder()
            .setLinks(Links.of(List.of(
                    BaseLink.newLinkBuilder()
                            .setType("tm:submodel")
                            .build()
            )))
            .build();

    private static final ThingModel KNOWN_FEATURE_LEVEL_TM = ThingModel.newBuilder()
            .setAtContext(AtContext.newMultipleAtContext(List.of(
                    SingleAtContext.newSinglePrefixedAtContext(DITTO_CONTEXT_PREFIX,
                            SingleUriAtContext.DITTO_WOT_EXTENSION)
            )))
            .setProperties(KNOWN_PROPERTIES)
            .setTmOptional(TmOptional.of(List.of(
                    TmOptionalElement.of("/properties/" + PROP_SOME_ARRAY_STRINGS),
                    TmOptionalElement.of("/properties/" + PROP_SOME_OBJECT)
            )))
            .setActions(KNOWN_ACTIONS)
            .setEvents(KNOWN_EVENTS)
            .build();

    private static final FeatureProperties KNOWN_FEATURE_PROPERTIES = FeatureProperties.newBuilder()
            .set(PROP_SOME_BOOL, true)
            .set(PROP_PATH_SOME_INT, 42)
            .set(PROP_SOME_NUMBER, 42.23)
            .set(PROP_PATH_SOME_STRING, "some")
            .build();


    private WotThingModelValidation sut;

    @Before
    public void setUp() {
        final TmValidationConfig validationConfig = mock(TmValidationConfig.class);
        when(validationConfig.isEnabled()).thenReturn(true);

        final FeatureValidationConfig featureValidationConfig = mock(FeatureValidationConfig.class);
        when(featureValidationConfig.isEnforceFeatureDescriptionModification()).thenReturn(true);
        when(featureValidationConfig.isEnforcePresenceOfModeledFeatures()).thenReturn(true);
        when(featureValidationConfig.isForbidNonModeledFeatures()).thenReturn(true);
        when(featureValidationConfig.isEnforceProperties()).thenReturn(true);
        when(featureValidationConfig.isEnforceDesiredProperties()).thenReturn(true);
        when(featureValidationConfig.isForbidNonModeledProperties()).thenReturn(true);
        when(featureValidationConfig.isForbidNonModeledDesiredProperties()).thenReturn(true);
        when(featureValidationConfig.isEnforceInboxMessagesInput()).thenReturn(true);
        when(featureValidationConfig.isEnforceInboxMessagesOutput()).thenReturn(true);
        when(featureValidationConfig.isEnforceOutboxMessages()).thenReturn(true);
        when(featureValidationConfig.isForbidNonModeledInboxMessages()).thenReturn(true);
        when(featureValidationConfig.isForbidNonModeledOutboxMessages()).thenReturn(true);
        when(validationConfig.getFeatureValidationConfig()).thenReturn(featureValidationConfig);

        sut = WotThingModelValidation.of(validationConfig);
    }

    @Test
    public void validateFeaturesDeletionSucceedsWithThingLevelModelNotHavingSubmodels() {
        internalCheckFail(false, sut.validateThingScopedDeletion(ThingModel.newBuilder().build(),
                Map.of(),
                JsonPointer.of("features"),
                provideValidationContext()
        ));
    }

    @Test
    public void validateFeaturesDeletionFailsWithThingLevelModelHavingSubmodels() {
        internalCheckFail(true, sut.validateThingScopedDeletion(KNOWN_THING_LEVEL_TM_WITH_SUBMODELS,
                Map.of(KNOWN_FEATURE_ID, KNOWN_FEATURE_LEVEL_TM),
                JsonPointer.of("features"),
                provideValidationContext()
        ));
    }

    @Test
    public void validateFeaturesPresenceSucceedsWhenAllModeledFeaturesArePresent() {
        checkValidateFeaturesPresence(Features.newBuilder()
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES)
                                .withId(KNOWN_FEATURE_ID)
                                .build())
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES)
                                .withId(KNOWN_FEATURE_ID_2)
                                .build())
                        .build(),
                false
        );
    }

    @Test
    public void validateFeaturesPresenceFailsWhenNotAllModeledFeaturesArePresent() {
        checkValidateFeaturesPresence(Features.newBuilder()
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES)
                                .withId(KNOWN_FEATURE_ID)
                                .build())
                        .build(),
                true
        );
    }

    @Test
    public void validateFeaturesPresenceFailsWhenNonModeledFeaturesAreProvided() {
        checkValidateFeaturesPresence(Features.newBuilder()
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES)
                                .withId(KNOWN_FEATURE_ID)
                                .build())
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES)
                                .withId(KNOWN_FEATURE_ID_2)
                                .build())
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES)
                                .withId("unknown-feature")
                                .build())
                        .build(),
                true
        );
    }

    @Test
    public void validateFeaturesPropertiesSucceeds() {
        checkValidateFeaturesProperties(Features.newBuilder()
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES)
                                .withId(KNOWN_FEATURE_ID)
                                .build())
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES)
                                .withId(KNOWN_FEATURE_ID_2)
                                .build())
                        .build(),
                false
        );
    }

    @Test
    public void validateFeaturesPropertiesFailsWhenMissingRequiredProperty() {
        checkValidateFeaturesProperties(Features.newBuilder()
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES.toBuilder().remove(PROP_SOME_BOOL).build())
                                .withId(KNOWN_FEATURE_ID)
                                .build())
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES)
                                .withId(KNOWN_FEATURE_ID_2)
                                .build())
                        .build(),
                true
        );
    }

    @Test
    public void validateFeaturesPropertiesFailsWhenPropertyHasWrongDatatype() {
        checkValidateFeaturesProperties(Features.newBuilder()
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES.toBuilder().set(PROP_SOME_BOOL, "not a bool").build())
                                .withId(KNOWN_FEATURE_ID)
                                .build())
                        .set(Feature.newBuilder()
                                .properties(KNOWN_FEATURE_PROPERTIES)
                                .withId(KNOWN_FEATURE_ID_2)
                                .build())
                        .build(),
                true
        );
    }

    @Test
    public void validateFeaturePresenceSucceeds() {
        checkValidateFeaturePresence(Feature.newBuilder()
                        .properties(KNOWN_FEATURE_PROPERTIES)
                        .withId(KNOWN_FEATURE_ID)
                        .build(),
                false
        );
    }

    @Test
    public void validateFeaturePresenceFails() {
        checkValidateFeaturePresence(Feature.newBuilder()
                        .properties(KNOWN_FEATURE_PROPERTIES)
                        .withId("unknown-id")
                        .build(),
                true
        );
    }

    @Test
    public void validateFeatureSucceeds() {
        checkValidateFeature(Feature.newBuilder()
                        .properties(KNOWN_FEATURE_PROPERTIES)
                        .withId(KNOWN_FEATURE_ID)
                        .build(),
                false
        );
    }

    @Test
    public void validateFeatureSucceedsWithOptionalDesiredPropertyPresent() {
        checkValidateFeature(Feature.newBuilder()
                        .properties(KNOWN_FEATURE_PROPERTIES)
                        .desiredProperties(FeatureProperties.newBuilder()
                                .set(PROP_PATH_SOME_INT, 42)
                                .build()
                        )
                        .withId(KNOWN_FEATURE_ID)
                        .build(),
                false
        );
    }

    @Test
    public void validateFeatureFailsWhenMissingRequiredProperty() {
        checkValidateFeature(Feature.newBuilder()
                        .properties(KNOWN_FEATURE_PROPERTIES.toBuilder().remove(PROP_PATH_SOME_STRING).build())
                        .withId(KNOWN_FEATURE_ID)
                        .build(),
                true
        );
    }

    @Test
    public void validateFeatureFailsWithOptionalDesiredPropertyHavingWrongDatatype() {
        checkValidateFeature(Feature.newBuilder()
                        .properties(KNOWN_FEATURE_PROPERTIES)
                        .desiredProperties(FeatureProperties.newBuilder()
                                .set(PROP_PATH_SOME_INT, "not an int")
                                .build()
                        )
                        .withId(KNOWN_FEATURE_ID)
                        .build(),
                true
        );
    }

    @Test
    public void validateFeaturePropertySucceedsForBooleanProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_SOME_BOOL, false, JsonValue.of(true), false);
    }

    @Test
    public void validateFeaturePropertiesSucceedsForBooleanProperty() {
        checkValidateFeatureProperties(PROP_SOME_BOOL, false, JsonValue.of(true), false);
    }

    @Test
    public void validateFeaturePropertyFailsForBooleanProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_SOME_BOOL, false, JsonValue.of("something else"), true);
    }

    @Test
    public void validateFeaturePropertiesFailsForBooleanProperty() {
        checkValidateFeatureProperties(PROP_SOME_BOOL, false, JsonValue.of("something else"), true);
    }

    @Test
    public void validateFeaturePropertySucceedsForIntegerProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_PATH_SOME_INT, false, JsonValue.of(42), false);
    }

    @Test
    public void validateFeaturePropertiesSucceedsForIntegerProperty() {
        checkValidateFeatureProperties(PROP_PATH_SOME_INT, false, JsonValue.of(42), false);
    }

    @Test
    public void validateFeaturePropertyFailsForIntegerProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_PATH_SOME_INT, false, JsonValue.of("something else"), true);
    }

    @Test
    public void validateFeaturePropertiesFailsForIntegerProperty() {
        checkValidateFeatureProperties(PROP_PATH_SOME_INT, false, JsonValue.of("something else"), true);
    }

    @Test
    public void validateFeaturePropertySucceedsForNumberProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_SOME_NUMBER, false, JsonValue.of(42.23), false);
    }

    @Test
    public void validateFeaturePropertiesSucceedsForNumberProperty() {
        checkValidateFeatureProperties(PROP_SOME_NUMBER, false, JsonValue.of(42.23), false);
    }

    @Test
    public void validateFeaturePropertyFailsForNumberProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_SOME_NUMBER, false, JsonValue.of("something else"), true);
    }

    @Test
    public void validateFeaturePropertiesFailsForNumberProperty() {
        checkValidateFeatureProperties(PROP_SOME_NUMBER, false, JsonValue.of("something else"), true);
    }

    @Test
    public void validateFeaturePropertySucceedsForStringProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_PATH_SOME_STRING, false, JsonValue.of("some"), false);
    }

    @Test
    public void validateFeaturePropertiesSucceedsForStringProperty() {
        checkValidateFeatureProperties(PROP_PATH_SOME_STRING, false, JsonValue.of("some"), false);
    }

    @Test
    public void validateFeaturePropertyFailsForStringProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_PATH_SOME_STRING, false, JsonValue.of(false), true);
    }

    @Test
    public void validateFeaturePropertiesFailsForStringProperty() {
        checkValidateFeatureProperties(PROP_PATH_SOME_STRING, false, JsonValue.of(false), true);
    }

    @Test
    public void validateFeaturePropertySucceedsForArraysStringProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_SOME_ARRAY_STRINGS, false,
                JsonArray.of("some", "string", "arr"),
                false);
    }

    @Test
    public void validateFeaturePropertiesSucceedsForArraysStringProperty() {
        checkValidateFeatureProperties(PROP_SOME_ARRAY_STRINGS, false, JsonArray.of("some", "string", "arr"), false);
    }

    @Test
    public void validateFeaturePropertyFailsForArraysStringProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_SOME_ARRAY_STRINGS, false, JsonArray.of(false, true, false),
                true);
    }

    @Test
    public void validateFeaturePropertiesFailsForArraysStringProperty() {
        checkValidateFeatureProperties(PROP_SOME_ARRAY_STRINGS, false, JsonArray.of(false, true, false), true);
    }

    @Test
    public void validateFeaturePropertySucceedsForObjectProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_SOME_OBJECT, false, PROP_KNOWN_SOME_OBJECT, false);
    }

    @Test
    public void validateFeaturePropertySucceedsForCategoryUpdateContainingAllRequiredProperties() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, CATEGORY_CONFIG, false, JsonObject.newBuilder()
                        .set(PROP_SOME_INT, 44)
                        .set(PROP_SOME_STRING, "some")
                        .build(),
                false
        );
    }

    @Test
    public void validateFeaturePropertyFailsForCategoryUpdateMissingRequiredProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, CATEGORY_CONFIG, false, JsonObject.newBuilder()
                        .set(PROP_SOME_INT, 44)
                        .build(),
                true
        );
    }

    @Test
    public void validateFeaturePropertyFailsForCategoryUpdateNotBeingObject() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, CATEGORY_CONFIG, false, JsonValue.of("not a category object"),
                true
        );
    }

    @Test
    public void validateFeaturePropertiesSucceedsForObjectProperty() {
        checkValidateFeatureProperties(PROP_SOME_OBJECT, false, PROP_KNOWN_SOME_OBJECT, false);
    }

    @Test
    public void validateFeaturePropertyFailsForObjectProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_SOME_OBJECT, false, JsonValue.of(false), true);
    }

    @Test
    public void validateFeaturePropertiesFailsForObjectProperty() {
        checkValidateFeatureProperties(PROP_SOME_OBJECT, false, JsonValue.of(false), true);
    }

    @Test
    public void validateFeaturePropertyFailsForObjectAttributeMissingRequiredField() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_SOME_ARRAY_STRINGS, false,
                PROP_KNOWN_SOME_OBJECT.toBuilder().remove(PROP_PATH_SOME_STRING).build(), true);
    }

    @Test
    public void validateFeaturePropertiesFailsForObjectAttributeMissingRequiredField() {
        checkValidateFeatureProperties(PROP_SOME_ARRAY_STRINGS, false,
                PROP_KNOWN_SOME_OBJECT.toBuilder().remove(PROP_PATH_SOME_STRING).build(), true);
    }

    @Test
    public void validateFeaturePropertyFailsForObjectAttributeAdditionalNotSpecifiedField() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, PROP_SOME_ARRAY_STRINGS, false,
                PROP_KNOWN_SOME_OBJECT.toBuilder().set("new", "foo").build(), true);
    }

    @Test
    public void validateFeaturePropertiesFailsForObjectAttributeAdditionalNotSpecifiedField() {
        checkValidateFeatureProperties(PROP_SOME_ARRAY_STRINGS, false,
                PROP_KNOWN_SOME_OBJECT.toBuilder().set("new", "foo").build(), true);
    }

    @Test
    public void validateFeaturePropertyFailsForNonModeledProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, "newStuff", false, JsonValue.of(true), true);
    }

    @Test
    public void validateFeaturePropertiesFailsForNonModeledProperties() {
        checkValidateFeatureProperties("newStuff", false, JsonValue.of(true), true);
    }

    @Test
    public void validateFeaturePropertyFailsForNonModeledDesiredProperty() {
        checkValidateFeatureProperty(KNOWN_FEATURE_ID, "newStuff", true, JsonValue.of(true), true);
    }

    @Test
    public void validateFeaturePropertiesFailsForNonModeledDesiredProperties() {
        checkValidateFeatureProperties("newStuff", true, JsonValue.of(true), true);
    }

    @Test
    public void validateFeaturePropertyDeletionSucceedsForOptionalArrayProperty() {
        checkValidateFeaturePropertyDeletion(
                JsonPointer.of("features/" + KNOWN_FEATURE_ID + "/properties/" + PROP_SOME_ARRAY_STRINGS),
                false
        );
    }

    @Test
    public void validateFeaturePropertyDeletionFailsForFeatureContainingRequiredProperties() {
        checkValidateFeaturePropertyDeletion(
                JsonPointer.of("features/" + KNOWN_FEATURE_ID),
                true
        );
    }

    @Test
    public void validateFeaturePropertyDeletionFailsForFeaturePropertiesContainingRequiredProperties() {
        checkValidateFeaturePropertyDeletion(
                JsonPointer.of("features/" + KNOWN_FEATURE_ID + "/properties/"),
                true
        );
    }

    @Test
    public void validateFeaturePropertyDeletionFailsForFeaturePropertiesCategoryContainingRequiredProperties() {
        checkValidateFeaturePropertyDeletion(
                JsonPointer.of("features/" + KNOWN_FEATURE_ID + "/properties/" + CATEGORY_CONFIG),
                true
        );
    }

    @Test
    public void validateFeatureActionBoolInputSucceeds() {
        checkValidateFeatureActionInput(KNOWN_FEATURE_ID, ACTION_PROCESS_BOOL, JsonValue.of(true), false);
    }

    @Test
    public void validateFeatureActionBoolInputFailsWrongDatatype() {
        checkValidateFeatureActionInput(KNOWN_FEATURE_ID, ACTION_PROCESS_BOOL, JsonValue.of("oh no"), true);
    }

    @Test
    public void validateFeatureActionObjectInputSucceeds() {
        checkValidateFeatureActionInput(KNOWN_FEATURE_ID, ACTION_PROCESS_OBJECT, JsonObject.newBuilder()
                .set(PROP_SOME_BOOL, false)
                .set(PROP_SOME_INT, 23)
                .set(PROP_SOME_STRING, "yes!")
                .build(), false);
    }

    @Test
    public void validateFeatureActionObjectInputFailsWrongDatatype() {
        checkValidateFeatureActionInput(KNOWN_FEATURE_ID, ACTION_PROCESS_OBJECT, JsonArray.empty(), true);
    }

    @Test
    public void validateFeatureActionObjectInputFailsMissingRequiredField() {
        checkValidateFeatureActionInput(KNOWN_FEATURE_ID, ACTION_PROCESS_OBJECT, JsonObject.newBuilder()
                .set(PROP_PATH_SOME_STRING, "yes!")
                .build(), true);
    }

    @Test
    public void validateNonModeledFeatureActionInputFails() {
        checkValidateFeatureActionInput(KNOWN_FEATURE_ID, "someNewAction", JsonValue.of(true), true);
    }

    @Test
    public void validateFeatureActionBoolOutputSucceeds() {
        checkValidateFeatureActionOutput(KNOWN_FEATURE_ID, ACTION_PROCESS_BOOL, JsonValue.of(true), false);
    }

    @Test
    public void validateFeatureActionBoolOutputFailsWrongDatatype() {
        checkValidateFeatureActionOutput(KNOWN_FEATURE_ID, ACTION_PROCESS_BOOL, JsonValue.of("oh no"), true);
    }

    @Test
    public void validateFeatureActionObjectOutputSucceeds() {
        checkValidateFeatureActionOutput(KNOWN_FEATURE_ID, ACTION_PROCESS_OBJECT, JsonObject.newBuilder()
                .set(PROP_SOME_BOOL, false)
                .set(PROP_SOME_INT, 23)
                .set(PROP_SOME_STRING, "yes!")
                .build(), false);
    }

    @Test
    public void validateFeatureActionObjectOutputFailsWrongDatatype() {
        checkValidateFeatureActionOutput(KNOWN_FEATURE_ID, ACTION_PROCESS_OBJECT, JsonArray.empty(), true);
    }

    @Test
    public void validateFeatureActionObjectOutputFailsMissingRequiredField() {
        checkValidateFeatureActionOutput(KNOWN_FEATURE_ID, ACTION_PROCESS_OBJECT, JsonObject.newBuilder()
                .set(PROP_PATH_SOME_STRING, "yes!")
                .build(), true);
    }

    @Test
    public void validateFeatureEventIntDataSucceeds() {
        checkValidateFeatureEventData(KNOWN_FEATURE_ID, EVENT_EMIT_INT, JsonValue.of(33), false);
    }

    @Test
    public void validateFeatureEventBoolDataFailsWrongDatatype() {
        checkValidateFeatureEventData(KNOWN_FEATURE_ID, EVENT_EMIT_INT, JsonValue.of("oh no"), true);
    }

    @Test
    public void validateFeatureEventArrayDataSucceeds() {
        checkValidateFeatureEventData(KNOWN_FEATURE_ID, EVENT_EMIT_ARRAY, JsonArray.newBuilder()
                .add(JsonObject.newBuilder()
                        .set(PROP_SOME_BOOL, false)
                        .set(PROP_SOME_INT, 23)
                        .set(PROP_SOME_STRING, "yes!")
                        .build()
                ).build(), false);
    }

    @Test
    public void validateFeatureEventArrayDataFailsWrongDatatype() {
        checkValidateFeatureEventData(KNOWN_FEATURE_ID, EVENT_EMIT_ARRAY, JsonObject.empty(), true);
    }

    @Test
    public void validateFeatureEventArrayDataFailsMissingRequiredFieldInsideObject() {
        checkValidateFeatureEventData(KNOWN_FEATURE_ID, EVENT_EMIT_ARRAY, JsonArray.newBuilder()
                .add(JsonObject.newBuilder()
                        .set(PROP_PATH_SOME_INT, 23)
                        .set(PROP_PATH_SOME_STRING, "yes!")
                        .build()
                ).build(), true);
    }

    @Test
    public void validateNonModeledFeatureEventDataFails() {
        checkValidateFeatureEventData(KNOWN_FEATURE_ID, "someNewEvent", JsonValue.of(true), true);
    }

    private void checkValidateFeaturesPresence(@Nullable final Features features, final boolean mustFail) {
        internalCheckFail(mustFail, sut.validateFeaturesPresence(Map.of(
                        KNOWN_FEATURE_ID, KNOWN_FEATURE_LEVEL_TM,
                        KNOWN_FEATURE_ID_2, KNOWN_FEATURE_LEVEL_TM
                ),
                features,
                provideValidationContext()
        ));
    }

    private void checkValidateFeaturesProperties(@Nullable final Features features, final boolean mustFail) {
        internalCheckFail(mustFail, sut.validateFeaturesProperties(Map.of(
                        KNOWN_FEATURE_ID, KNOWN_FEATURE_LEVEL_TM,
                        KNOWN_FEATURE_ID_2, KNOWN_FEATURE_LEVEL_TM
                ),
                features,
                JsonPointer.of("features"),
                provideValidationContext()
        ));
    }

    private void checkValidateFeaturePresence(final Feature feature, final boolean mustFail) {
        internalCheckFail(mustFail, sut.validateFeaturePresence(Map.of(
                        KNOWN_FEATURE_ID, KNOWN_FEATURE_LEVEL_TM,
                        KNOWN_FEATURE_ID_2, KNOWN_FEATURE_LEVEL_TM
                ),
                feature,
                provideValidationContext()
        ));
    }

    private void checkValidateFeature(final Feature feature, final boolean mustFail) {
        internalCheckFail(mustFail, sut.validateFeature(KNOWN_FEATURE_LEVEL_TM,
                feature,
                JsonPointer.of("features/" + feature.getId()),
                provideValidationContext()
        ));
    }

    private void checkValidateFeatureProperty(final String featureId, final CharSequence propertyPath,
            final boolean desiredProperty,
            final JsonValue propertyValue,
            final boolean mustFail
    ) {
        final JsonPointer propertyPointer = JsonPointer.of(propertyPath);
        internalCheckFail(mustFail, sut.validateFeatureProperty(KNOWN_FEATURE_LEVEL_TM,
                KNOWN_FEATURE_ID,
                propertyPointer,
                propertyValue,
                desiredProperty,
                JsonPointer.of("features/" + featureId + "/properties").append(propertyPointer),
                provideValidationContext()
        ));
    }

    private void checkValidateFeatureProperties(final CharSequence propertyPath, final boolean desiredProperties,
            final JsonValue propertyValue,
            final boolean mustFail
    ) {
        internalCheckFail(mustFail, sut.validateFeatureProperties(KNOWN_FEATURE_LEVEL_TM,
                KNOWN_FEATURE_ID,
                KNOWN_FEATURE_PROPERTIES.toBuilder()
                        .set(propertyPath, propertyValue)
                        .build(),
                desiredProperties,
                JsonPointer.of("attributes"),
                provideValidationContext()
        ));
    }

    private void checkValidateFeaturePropertyDeletion(final JsonPointer resourcePath, final boolean mustFail) {
        internalCheckFail(mustFail, sut.validateFeatureScopedDeletion(Map.of(KNOWN_FEATURE_ID, KNOWN_FEATURE_LEVEL_TM),
                KNOWN_FEATURE_LEVEL_TM,
                KNOWN_FEATURE_ID,
                resourcePath,
                provideValidationContext()
        ));
    }

    private void checkValidateFeatureActionInput(final String featureId, final String actionName,
            @Nullable final JsonValue inputData,
            final boolean mustFail
    ) {
        internalCheckFail(mustFail, sut.validateFeatureActionInput(KNOWN_FEATURE_LEVEL_TM,
                featureId,
                actionName,
                inputData,
                JsonPointer.of("features/" + featureId + "/inbox/messages/" + actionName),
                provideValidationContext()
        ));
    }

    private void checkValidateFeatureActionOutput(final String featureId, final String actionName,
            @Nullable final JsonValue outputData,
            final boolean mustFail
    ) {
        internalCheckFail(mustFail, sut.validateFeatureActionOutput(KNOWN_FEATURE_LEVEL_TM,
                featureId,
                actionName,
                outputData,
                JsonPointer.of("features/" + featureId + "/inbox/messages/" + actionName),
                provideValidationContext()
        ));
    }

    private void checkValidateFeatureEventData(final String featureId, final String eventName,
            @Nullable final JsonValue data,
            final boolean mustFail
    ) {
        internalCheckFail(mustFail, sut.validateFeatureEventData(KNOWN_FEATURE_LEVEL_TM,
                featureId,
                eventName,
                data,
                JsonPointer.of("features/" + featureId + "/outbox/messages/" + eventName),
                provideValidationContext()
        ));
    }

    private static ValidationContext provideValidationContext() {
        return ValidationContext.buildValidationContext(DittoHeaders.empty(), null, null);
    }

    private static void internalCheckFail(final boolean mustFail, final CompletionStage<Void> stage) {
        if (mustFail) {
            assertThat(stage)
                    .isCompletedExceptionally()
                    .failsWithin(50, TimeUnit.MILLISECONDS)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(WotThingModelPayloadValidationException.class);
        } else {
            assertThat(stage).isNotCompletedExceptionally().isDone();
        }
    }
}