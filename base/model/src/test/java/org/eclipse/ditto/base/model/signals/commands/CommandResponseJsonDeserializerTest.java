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
package org.eclipse.ditto.base.model.signals.commands;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.HttpStatusCodeOutOfRangeException;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for {@link CommandResponseJsonDeserializer}.
 */
public final class CommandResponseJsonDeserializerTest {

    private static final String FAKE_TYPE = "things.responses:modifyZoiglfrex";

    private static CommandResponseJsonDeserializer.DeserializationFunction<CommandResponse<?>>
            voidDeserializationFunction;

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    private DittoHeaders dittoHeaders;

    @BeforeClass
    public static void beforeClass() {
        voidDeserializationFunction = context -> null;
    }

    @Before
    public void before() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

    @Test
    public void newInstanceWithNullTypeThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> CommandResponseJsonDeserializer.newInstance(null, voidDeserializationFunction))
                .withMessage("The type must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithEmptyTypeThrowsException() {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> CommandResponseJsonDeserializer.newInstance("", voidDeserializationFunction))
                .withMessage("The type must not be empty or blank.")
                .withNoCause();
    }

    @Test
    public void newInstanceWithBlankTypeThrowsException() {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> CommandResponseJsonDeserializer.newInstance("   ", voidDeserializationFunction))
                .withMessage("The type must not be empty or blank.")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullDeserializationFunctionThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> CommandResponseJsonDeserializer.newInstance(FAKE_TYPE, null))
                .withMessage("The deserializationFunction must not be null!")
                .withNoCause();
    }

    @Test
    public void callDeserializeWithNullJsonObjectThrowsException() {
        final CommandResponseJsonDeserializer<CommandResponse<?>> underTest =
                CommandResponseJsonDeserializer.newInstance(FAKE_TYPE, voidDeserializationFunction);

        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> underTest.deserialize(null, dittoHeaders))
                .withMessage("The jsonObject must not be null!")
                .withNoCause();
    }

    @Test
    public void callDeserializeWithNullDittoHeadersThrowsException() {
        final CommandResponseJsonDeserializer<CommandResponse<?>> underTest =
                CommandResponseJsonDeserializer.newInstance(FAKE_TYPE, voidDeserializationFunction);

        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> underTest.deserialize(JsonObject.empty(), null))
                .withMessage("The dittoHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void deserializeJsonWithoutTypeField() {
        final CommandResponseJsonDeserializer<CommandResponse<?>> underTest =
                CommandResponseJsonDeserializer.newInstance(FAKE_TYPE, voidDeserializationFunction);

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.deserialize(JsonObject.empty(), dittoHeaders))
                .withMessage("Failed to deserialize JSON object to a command response of type <%s>: %s",
                        FAKE_TYPE,
                        "JSON did not include required </type> field!")
                .withCauseInstanceOf(JsonMissingFieldException.class);
    }

    @Test
    public void deserializeJsonWithInvalidTypeValueField() {
        final int invalidTypeValue = 1;
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE.getPointer(), invalidTypeValue)
                .build();
        final CommandResponseJsonDeserializer<CommandResponse<?>> underTest =
                CommandResponseJsonDeserializer.newInstance(FAKE_TYPE, voidDeserializationFunction);

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.deserialize(jsonObject, dittoHeaders))
                .withMessage("Failed to deserialize JSON object to a command response of type <%s>:" +
                                " Value <%d> for </type> is not of type <String>!",
                        FAKE_TYPE,
                        invalidTypeValue)
                .withCauseInstanceOf(JsonParseException.class);
    }

    @Test
    public void deserializeJsonWithUnexpectedType() {
        final String unexpectedType = "foo.bar:baz";
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE, unexpectedType)
                .build();
        final CommandResponseJsonDeserializer<CommandResponse<?>> underTest =
                CommandResponseJsonDeserializer.newInstance(FAKE_TYPE, voidDeserializationFunction);

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.deserialize(jsonObject, dittoHeaders))
                .withMessage("Failed to deserialize JSON object to a command response of type <%s>: " +
                                "Value <%s> for </type> does not match <%s>.",
                        FAKE_TYPE,
                        unexpectedType,
                        FAKE_TYPE)
                .withCauseInstanceOf(JsonParseException.class);
    }

    @Test
    public void deserializeJsonWithoutHttpStatusCodeField() {
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE, FAKE_TYPE)
                .build();
        final CommandResponseJsonDeserializer<CommandResponse<?>> underTest =
                CommandResponseJsonDeserializer.newInstance(FAKE_TYPE, voidDeserializationFunction);

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.deserialize(jsonObject, dittoHeaders))
                .withMessage("Failed to deserialize JSON object to a command response of type <%s>: %s",
                        FAKE_TYPE,
                        "JSON did not include required </status> field!")
                .withCauseInstanceOf(JsonMissingFieldException.class);
    }

    @Test
    public void deserializeJsonWithInvalidHttpStatusCodeValueField() {
        final boolean invalidHttpStatusCodeValue = true;
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE, FAKE_TYPE)
                .set(CommandResponse.JsonFields.STATUS.getPointer(), invalidHttpStatusCodeValue)
                .build();
        final CommandResponseJsonDeserializer<CommandResponse<?>> underTest =
                CommandResponseJsonDeserializer.newInstance(FAKE_TYPE, voidDeserializationFunction);

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.deserialize(jsonObject, dittoHeaders))
                .withMessage("Failed to deserialize JSON object to a command response of type <%s>:" +
                                " Value <%s> for </status> is not of type <Integer>!",
                        FAKE_TYPE,
                        invalidHttpStatusCodeValue)
                .withCauseInstanceOf(JsonParseException.class);
    }

    @Test
    public void deserializeJsonWithInvalidHttpStatusCodeValueField2() {
        final int invalidHttpStatusCodeValue = 999;
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE, FAKE_TYPE)
                .set(CommandResponse.JsonFields.STATUS, invalidHttpStatusCodeValue)
                .build();
        final CommandResponseJsonDeserializer<CommandResponse<?>> underTest =
                CommandResponseJsonDeserializer.newInstance(FAKE_TYPE, voidDeserializationFunction);

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.deserialize(jsonObject, dittoHeaders))
                .withMessage("Failed to deserialize JSON object to a command response of type <%s>:" +
                                " Provided HTTP status code <%d> is not within the range of 100 to 599.",
                        FAKE_TYPE,
                        invalidHttpStatusCodeValue)
                .withCauseInstanceOf(HttpStatusCodeOutOfRangeException.class);
    }

    @Test
    public void deserializeWhenDeserializationFunctionThrowsException() {
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE, FAKE_TYPE)
                .set(CommandResponse.JsonFields.STATUS, HttpStatus.CREATED.getCode())
                .build();
        final IllegalArgumentException illegalArgumentException = new IllegalArgumentException("This is not so good.");
        final CommandResponseJsonDeserializer.DeserializationFunction<CommandResponse<?>> deserializationFunction =
                httpStatus -> {
                    throw illegalArgumentException;
                };
        final CommandResponseJsonDeserializer<CommandResponse<?>> underTest =
                CommandResponseJsonDeserializer.newInstance(FAKE_TYPE, deserializationFunction);

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.deserialize(jsonObject, dittoHeaders))
                .withCause(illegalArgumentException);
    }

}
