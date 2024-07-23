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
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.wot.model.Action;
import org.eclipse.ditto.wot.model.Actions;
import org.eclipse.ditto.wot.model.AtContext;
import org.eclipse.ditto.wot.model.Event;
import org.eclipse.ditto.wot.model.Events;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.TmOptional;
import org.eclipse.ditto.wot.model.TmOptionalElement;
import org.eclipse.ditto.wot.validation.config.ThingValidationConfig;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;
import org.junit.Before;
import org.junit.Test;

/**
 * Provides unit tests for testing the "thing" related functionality of {@link WotThingModelValidation}.
 */
public final class WotThingModelValidationThingLevelTest {

    private static final String PROP_SOME_BOOL = "someBool";
    private static final String PROP_SOME_INT = "someInt";
    private static final String PROP_SOME_NUMBER = "someNumber";
    private static final String PROP_SOME_STRING = "someString";
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
                    .build(),
            Property.newBuilder(PROP_SOME_NUMBER)
                    .setSchema(SingleDataSchema.newNumberSchemaBuilder().build())
                    .build(),
            Property.newBuilder(PROP_SOME_STRING)
                    .setSchema(SingleDataSchema.newStringSchemaBuilder().build())
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

    private static final ThingModel KNOWN_THING_LEVEL_TM = ThingModel.newBuilder()
            .setAtContext(AtContext.newSingleUriAtContext("foo"))
            .setProperties(KNOWN_PROPERTIES)
            .setTmOptional(TmOptional.of(List.of(
                    TmOptionalElement.of("/properties/" + PROP_SOME_ARRAY_STRINGS),
                    TmOptionalElement.of("/properties/" + PROP_SOME_OBJECT)
            )))
            .setActions(KNOWN_ACTIONS)
            .setEvents(KNOWN_EVENTS)
            .build();

    private static final Attributes KNOWN_THING_ATTRIBUTES = Attributes.newBuilder()
            .set(PROP_SOME_BOOL, true)
            .set(PROP_SOME_INT, 42)
            .set(PROP_SOME_NUMBER, 42.23)
            .set(PROP_SOME_STRING, "some")
            .build();


    private WotThingModelValidation sut;

    @Before
    public void setUp() {
        final TmValidationConfig validationConfig = mock(TmValidationConfig.class);
        when(validationConfig.isEnabled()).thenReturn(true);

        final ThingValidationConfig thingValidationConfig = mock(ThingValidationConfig.class);
        when(thingValidationConfig.isEnforceAttributes()).thenReturn(true);
        when(thingValidationConfig.isForbidNonModeledAttributes()).thenReturn(true);
        when(thingValidationConfig.isEnforceInboxMessagesInput()).thenReturn(true);
        when(thingValidationConfig.isEnforceInboxMessagesOutput()).thenReturn(true);
        when(thingValidationConfig.isEnforceOutboxMessages()).thenReturn(true);
        when(thingValidationConfig.isForbidNonModeledInboxMessages()).thenReturn(true);
        when(thingValidationConfig.isForbidNonModeledOutboxMessages()).thenReturn(true);
        when(validationConfig.getThingValidationConfig()).thenReturn(thingValidationConfig);

        sut = WotThingModelValidation.of(validationConfig);
    }

    @Test
    public void validateThingAttributeSucceedsForBooleanAttribute() {
        checkValidateThingAttribute(PROP_SOME_BOOL, JsonValue.of(true), false);
    }

    @Test
    public void validateThingAttributesSucceedsForBooleanAttribute() {
        checkValidateThingAttributes(PROP_SOME_BOOL, JsonValue.of(true), false);
    }

    @Test
    public void validateThingAttributeFailsForBooleanAttribute() {
        checkValidateThingAttribute(PROP_SOME_BOOL, JsonValue.of("something else"), true);
    }

    @Test
    public void validateThingAttributesFailsForBooleanAttribute() {
        checkValidateThingAttributes(PROP_SOME_BOOL, JsonValue.of("something else"), true);
    }

    @Test
    public void validateThingAttributeSucceedsForIntegerAttribute() {
        checkValidateThingAttribute(PROP_SOME_INT, JsonValue.of(42), false);
    }

    @Test
    public void validateThingAttributesSucceedsForIntegerAttribute() {
        checkValidateThingAttributes(PROP_SOME_INT, JsonValue.of(42), false);
    }

    @Test
    public void validateThingAttributeFailsForIntegerAttribute() {
        checkValidateThingAttribute(PROP_SOME_INT, JsonValue.of("something else"), true);
    }

    @Test
    public void validateThingAttributesFailsForIntegerAttribute() {
        checkValidateThingAttributes(PROP_SOME_INT, JsonValue.of("something else"), true);
    }

    @Test
    public void validateThingAttributeSucceedsForNumberAttribute() {
        checkValidateThingAttribute(PROP_SOME_NUMBER, JsonValue.of(42.23), false);
    }

    @Test
    public void validateThingAttributesSucceedsForNumberAttribute() {
        checkValidateThingAttributes(PROP_SOME_NUMBER, JsonValue.of(42.23), false);
    }

    @Test
    public void validateThingAttributeFailsForNumberAttribute() {
        checkValidateThingAttribute(PROP_SOME_NUMBER, JsonValue.of("something else"), true);
    }

    @Test
    public void validateThingAttributesFailsForNumberAttribute() {
        checkValidateThingAttributes(PROP_SOME_NUMBER, JsonValue.of("something else"), true);
    }

    @Test
    public void validateThingAttributeSucceedsForStringAttribute() {
        checkValidateThingAttribute(PROP_SOME_STRING, JsonValue.of("some"), false);
    }

    @Test
    public void validateThingAttributesSucceedsForStringAttribute() {
        checkValidateThingAttributes(PROP_SOME_STRING, JsonValue.of("some"), false);
    }

    @Test
    public void validateThingAttributeFailsForStringAttribute() {
        checkValidateThingAttribute(PROP_SOME_STRING, JsonValue.of(false), true);
    }

    @Test
    public void validateThingAttributesFailsForStringAttribute() {
        checkValidateThingAttributes(PROP_SOME_STRING, JsonValue.of(false), true);
    }

    @Test
    public void validateThingAttributeSucceedsForArraysStringAttribute() {
        checkValidateThingAttribute(PROP_SOME_ARRAY_STRINGS, JsonArray.of("some", "string", "arr"), false);
    }

    @Test
    public void validateThingAttributesSucceedsForArraysStringAttribute() {
        checkValidateThingAttributes(PROP_SOME_ARRAY_STRINGS, JsonArray.of("some", "string", "arr"), false);
    }

    @Test
    public void validateThingAttributeFailsForArraysStringAttribute() {
        checkValidateThingAttribute(PROP_SOME_ARRAY_STRINGS, JsonArray.of(false, true, false), true);
    }

    @Test
    public void validateThingAttributesFailsForArraysStringAttribute() {
        checkValidateThingAttributes(PROP_SOME_ARRAY_STRINGS, JsonArray.of(false, true, false), true);
    }

    @Test
    public void validateThingAttributeSucceedsForObjectAttribute() {
        checkValidateThingAttribute(PROP_SOME_OBJECT, PROP_KNOWN_SOME_OBJECT, false);
    }

    @Test
    public void validateThingAttributesSucceedsForObjectAttribute() {
        checkValidateThingAttributes(PROP_SOME_OBJECT, PROP_KNOWN_SOME_OBJECT, false);
    }

    @Test
    public void validateThingAttributeFailsForObjectAttribute() {
        checkValidateThingAttribute(PROP_SOME_OBJECT, JsonValue.of(false), true);
    }

    @Test
    public void validateThingAttributesFailsForObjectAttribute() {
        checkValidateThingAttributes(PROP_SOME_OBJECT, JsonValue.of(false), true);
    }

    @Test
    public void validateThingAttributeFailsForObjectAttributeMissingRequiredField() {
        checkValidateThingAttribute(PROP_SOME_ARRAY_STRINGS,
                PROP_KNOWN_SOME_OBJECT.toBuilder().remove(PROP_SOME_STRING).build(), true);
    }

    @Test
    public void validateThingAttributesFailsForObjectAttributeMissingRequiredField() {
        checkValidateThingAttributes(PROP_SOME_ARRAY_STRINGS,
                PROP_KNOWN_SOME_OBJECT.toBuilder().remove(PROP_SOME_STRING).build(), true);
    }

    @Test
    public void validateThingAttributeFailsForObjectAttributeAdditionalNotSpecifiedField() {
        checkValidateThingAttribute(PROP_SOME_ARRAY_STRINGS,
                PROP_KNOWN_SOME_OBJECT.toBuilder().set("new", "foo").build(), true);
    }

    @Test
    public void validateThingAttributesFailsForObjectAttributeAdditionalNotSpecifiedField() {
        checkValidateThingAttributes(PROP_SOME_ARRAY_STRINGS,
                PROP_KNOWN_SOME_OBJECT.toBuilder().set("new", "foo").build(), true);
    }

    @Test
    public void validateThingAttributeFailsForNonModeledAttribute() {
        checkValidateThingAttribute("newStuff", JsonValue.of(true), true);
    }

    @Test
    public void validateThingAttributesFailsForNonModeledAttribute() {
        checkValidateThingAttributes("newStuff", JsonValue.of(true), true);
    }

    @Test
    public void validateThingAttributeDeletionFailsForRequiredBooleanAttribute() {
        checkValidateThingAttributeDeletion(PROP_SOME_BOOL, true);
    }

    @Test
    public void validateThingAttributeDeletionSucceedsForOptionalArrayAttribute() {
        checkValidateThingAttributeDeletion(PROP_SOME_ARRAY_STRINGS, false);
    }

    @Test
    public void validateThingActionBoolInputSucceeds() {
        checkValidateThingActionInput(ACTION_PROCESS_BOOL, JsonValue.of(true), false);
    }

    @Test
    public void validateThingActionBoolInputFailsWrongDatatype() {
        checkValidateThingActionInput(ACTION_PROCESS_BOOL, JsonValue.of("oh no"), true);
    }

    @Test
    public void validateThingActionObjectInputSucceeds() {
        checkValidateThingActionInput(ACTION_PROCESS_OBJECT, JsonObject.newBuilder()
                .set(PROP_SOME_BOOL, false)
                .set(PROP_SOME_INT, 23)
                .set(PROP_SOME_STRING, "yes!")
                .build(), false);
    }

    @Test
    public void validateThingActionObjectInputFailsWrongDatatype() {
        checkValidateThingActionInput(ACTION_PROCESS_OBJECT, JsonArray.empty(), true);
    }

    @Test
    public void validateThingActionObjectInputFailsMissingRequiredField() {
        checkValidateThingActionInput(ACTION_PROCESS_OBJECT, JsonObject.newBuilder()
                .set(PROP_SOME_STRING, "yes!")
                .build(), true);
    }

    @Test
    public void validateNonModeledThingActionInputFails() {
        checkValidateThingActionInput("someNewAction", JsonValue.of(true), true);
    }

    @Test
    public void validateThingActionBoolOutputSucceeds() {
        checkValidateThingActionOutput(ACTION_PROCESS_BOOL, JsonValue.of(true), false);
    }

    @Test
    public void validateThingActionBoolOutputFailsWrongDatatype() {
        checkValidateThingActionOutput(ACTION_PROCESS_BOOL, JsonValue.of("oh no"), true);
    }

    @Test
    public void validateThingActionObjectOutputSucceeds() {
        checkValidateThingActionOutput(ACTION_PROCESS_OBJECT, JsonObject.newBuilder()
                .set(PROP_SOME_BOOL, false)
                .set(PROP_SOME_INT, 23)
                .set(PROP_SOME_STRING, "yes!")
                .build(), false);
    }

    @Test
    public void validateThingActionObjectOutputFailsWrongDatatype() {
        checkValidateThingActionOutput(ACTION_PROCESS_OBJECT, JsonArray.empty(), true);
    }

    @Test
    public void validateThingActionObjectOutputFailsMissingRequiredField() {
        checkValidateThingActionOutput(ACTION_PROCESS_OBJECT, JsonObject.newBuilder()
                .set(PROP_SOME_STRING, "yes!")
                .build(), true);
    }

    @Test
    public void validateThingEventIntDataSucceeds() {
        checkValidateThingEventData(EVENT_EMIT_INT, JsonValue.of(33), false);
    }

    @Test
    public void validateThingEventBoolDataFailsWrongDatatype() {
        checkValidateThingEventData(EVENT_EMIT_INT, JsonValue.of("oh no"), true);
    }

    @Test
    public void validateThingEventArrayDataSucceeds() {
        checkValidateThingEventData(EVENT_EMIT_ARRAY, JsonArray.newBuilder()
                .add(JsonObject.newBuilder()
                        .set(PROP_SOME_BOOL, false)
                        .set(PROP_SOME_INT, 23)
                        .set(PROP_SOME_STRING, "yes!")
                        .build()
                ).build(), false);
    }

    @Test
    public void validateThingEventArrayDataFailsWrongDatatype() {
        checkValidateThingEventData(EVENT_EMIT_ARRAY, JsonObject.empty(), true);
    }

    @Test
    public void validateThingEventArrayDataFailsMissingRequiredFieldInsideObject() {
        checkValidateThingEventData(EVENT_EMIT_ARRAY, JsonArray.newBuilder()
                .add(JsonObject.newBuilder()
                        .set(PROP_SOME_INT, 23)
                        .set(PROP_SOME_STRING, "yes!")
                        .build()
                ).build(), true);
    }

    @Test
    public void validateNonModeledThingEventDataFails() {
        checkValidateThingEventData("someNewEvent", JsonValue.of(true), true);
    }

    private void checkValidateThingAttribute(final CharSequence attributePath, final JsonValue attributeValue,
            final boolean mustFail
    ) {
        final JsonPointer attributePointer = JsonPointer.of(attributePath);
        internalCheckFail(mustFail, sut.validateThingAttribute(KNOWN_THING_LEVEL_TM,
                attributePointer,
                attributeValue,
                JsonPointer.of("attributes").append(attributePointer),
                provideValidationContext()
        ));
    }

    private void checkValidateThingAttributes(final CharSequence attributePath, final JsonValue attributeValue,
            final boolean mustFail
    ) {
        internalCheckFail(mustFail, sut.validateThingAttributes(KNOWN_THING_LEVEL_TM,
                KNOWN_THING_ATTRIBUTES.toBuilder()
                        .set(attributePath, attributeValue)
                        .build(),
                JsonPointer.of("attributes"),
                provideValidationContext()
        ));
    }

    private void checkValidateThingAttributeDeletion(final CharSequence attributePath, final boolean mustFail) {
        internalCheckFail(mustFail, sut.validateThingScopedDeletion(KNOWN_THING_LEVEL_TM,
                Map.of(),
                JsonPointer.of("attributes/" + attributePath),
                provideValidationContext()
        ));
    }

    private void checkValidateThingActionInput(final String actionName, @Nullable final JsonValue inputData,
            final boolean mustFail
    ) {
        internalCheckFail(mustFail, sut.validateThingActionInput(KNOWN_THING_LEVEL_TM,
                actionName,
                inputData,
                JsonPointer.of("inbox/messages/" + actionName),
                provideValidationContext()
        ));
    }

    private void checkValidateThingActionOutput(final String actionName, @Nullable final JsonValue outputData,
            final boolean mustFail
    ) {
        internalCheckFail(mustFail, sut.validateThingActionOutput(KNOWN_THING_LEVEL_TM,
                actionName,
                outputData,
                JsonPointer.of("inbox/messages/" + actionName),
                provideValidationContext()
        ));
    }

    private void checkValidateThingEventData(final String eventName, @Nullable final JsonValue data,
            final boolean mustFail
    ) {
        internalCheckFail(mustFail, sut.validateThingEventData(KNOWN_THING_LEVEL_TM,
                eventName,
                data,
                JsonPointer.of("outbox/messages/" + eventName),
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