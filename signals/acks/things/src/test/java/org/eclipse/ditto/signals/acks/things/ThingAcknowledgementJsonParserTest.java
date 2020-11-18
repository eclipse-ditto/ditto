/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.acks.things;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementFactory.JsonParser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Unit test for {@link JsonParser}.
 */
public final class ThingAcknowledgementJsonParserTest {

    private static final AcknowledgementLabel KNOWN_LABEL = DittoAcknowledgementLabel.TWIN_PERSISTED;
    private static final ThingId KNOWN_THING_ID = ThingId.generateRandom();
    private static final JsonObject KNOWN_PAYLOAD = JsonObject.newBuilder().set("foo", "bar").build();

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;
    private JsonParser underTest;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        underTest = ThingAcknowledgementFactory.getJsonParser();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonParser.class, areImmutable());
    }

    @Test
    public void parseNullJsonObject() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The jsonObject must not be null!")
                .withNoCause();
    }

    @Test
    public void parseValidJsonRepresentationWithPayload() {
        final Acknowledgement acknowledgement =
                Acknowledgement.of(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK, dittoHeaders, KNOWN_PAYLOAD);
        final JsonObject jsonRepresentation = acknowledgement.toJson();

        final Acknowledgement parsedAcknowledgement = underTest.apply(jsonRepresentation);

        assertThat(parsedAcknowledgement).isEqualTo(acknowledgement);
    }

    @Test
    public void parseValidJsonRepresentationWithoutPayload() {
        final Acknowledgement acknowledgement =
                Acknowledgement.of(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK, dittoHeaders);
        final JsonObject jsonRepresentation = acknowledgement.toJson();

        final Acknowledgement parsedAcknowledgement = underTest.apply(jsonRepresentation);

        assertThat(parsedAcknowledgement).isEqualTo(acknowledgement);
    }

    @Test
    public void parseJsonRepresentationWithoutLabel() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK,
                        dittoHeaders);
        final JsonFieldDefinition<String> labelFieldDefinition = Acknowledgement.JsonFields.LABEL;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .remove(labelFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessageContaining(labelFieldDefinition.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithInvalidLabel() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK,
                        dittoHeaders);
        final JsonFieldDefinition<String> labelFieldDefinition = Acknowledgement.JsonFields.LABEL;
        final String invalidLabel = "19";
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .set(labelFieldDefinition, invalidLabel)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessage("Acknowledgement label <%s> is invalid.", invalidLabel,
                        labelFieldDefinition.getPointer())
                .withCauseInstanceOf(AcknowledgementLabelInvalidException.class);
    }

    @Test
    public void parseJsonRepresentationWithoutEntityId() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK,
                        dittoHeaders);
        final JsonFieldDefinition<String> entityIdFieldDefinition = Acknowledgement.JsonFields.ENTITY_ID;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .remove(entityIdFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessageContaining(entityIdFieldDefinition.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithInvalidEntityId() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK,
                        dittoHeaders);
        final JsonFieldDefinition<String> entityIdFieldDefinition = Acknowledgement.JsonFields.ENTITY_ID;
        final String invalidThingId = "abc{}";
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .set(entityIdFieldDefinition, invalidThingId)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessage("Thing ID '%s' is not valid!", invalidThingId)
                .withCauseInstanceOf(ThingIdInvalidException.class);
    }

    @Test
    public void parseJsonRepresentationWithoutEntityType() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK,
                        dittoHeaders);
        final JsonFieldDefinition<String> entityTypeFieldDefinition = Acknowledgement.JsonFields.ENTITY_TYPE;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .remove(entityTypeFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessageContaining(entityTypeFieldDefinition.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithUnexpectedEntityType() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK,
                        dittoHeaders);
        final JsonFieldDefinition<String> entityTypeFieldDefinition = Acknowledgement.JsonFields.ENTITY_TYPE;
        final EntityType unexpectedEntityType = EntityType.of("plumbus");
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .set(entityTypeFieldDefinition, unexpectedEntityType.toString())
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessage("The read entity type <%s> differs from the expected <%s>!", unexpectedEntityType,
                        KNOWN_THING_ID.getEntityType())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithoutStatusCode() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK,
                        dittoHeaders);
        final JsonFieldDefinition<?> statusCodeFieldDefinition = Acknowledgement.JsonFields.STATUS_CODE;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .remove(statusCodeFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessageContaining(statusCodeFieldDefinition.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithUnknownStatusCode() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK,
                        dittoHeaders);
        final JsonFieldDefinition<Integer> statusCodeFieldDefinition = Acknowledgement.JsonFields.STATUS_CODE;
        final int unknownStatusCode = 19;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .set(statusCodeFieldDefinition, unknownStatusCode)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessage("Status code <%d> is not supported!", unknownStatusCode)
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithoutDittoHeaders() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK,
                        dittoHeaders);
        final JsonFieldDefinition<?> dittoHeadersFieldDefinition = Acknowledgement.JsonFields.DITTO_HEADERS;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .remove(dittoHeadersFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessageContaining(dittoHeadersFieldDefinition.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithInvalidDittoHeaders() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, HttpStatusCode.OK,
                        dittoHeaders);
        final JsonValue invalidDittoHeaders = JsonValue.of("dittoHeaders");
        final JsonFieldDefinition<?> dittoHeadersFieldDefinition = Acknowledgement.JsonFields.DITTO_HEADERS;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .set(dittoHeadersFieldDefinition.getPointer(), invalidDittoHeaders)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessage("Value <%s> for <%s> is not of type <JsonObject>!", invalidDittoHeaders,
                        dittoHeadersFieldDefinition.getPointer())
                .withNoCause();
    }

}
