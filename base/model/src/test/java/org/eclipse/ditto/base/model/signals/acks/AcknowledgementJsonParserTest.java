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
package org.eclipse.ditto.base.model.signals.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.HttpStatusCodeOutOfRangeException;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Unit test for {@link AcknowledgementJsonParser}.
 */
public final class AcknowledgementJsonParserTest {

    private static final AcknowledgementLabel KNOWN_LABEL = DittoAcknowledgementLabel.TWIN_PERSISTED;
    private static final EntityId KNOWN_ENTITY_ID = EntityId.of(EntityType.of("thing"), "namespace:name");
    private static final JsonObject KNOWN_PAYLOAD = JsonObject.newBuilder().set("foo", "bar").build();

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;
    private AcknowledgementJsonParser underTest;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        underTest = new AcknowledgementJsonParser();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(AcknowledgementJsonParser.class, areImmutable());
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
                Acknowledgement.of(KNOWN_LABEL, KNOWN_ENTITY_ID, HttpStatus.OK, dittoHeaders, KNOWN_PAYLOAD);
        final JsonObject jsonRepresentation = acknowledgement.toJson();

        final Acknowledgement parsedAcknowledgement = underTest.apply(jsonRepresentation);

        assertThat(parsedAcknowledgement).isEqualTo(acknowledgement);
    }

    @Test
    public void parseValidJsonRepresentationWithoutPayload() {
        final Acknowledgement acknowledgement =
                Acknowledgement.of(KNOWN_LABEL, KNOWN_ENTITY_ID, HttpStatus.OK, dittoHeaders);
        final JsonObject jsonRepresentation = acknowledgement.toJson();

        final Acknowledgement parsedAcknowledgement = underTest.apply(jsonRepresentation);

        assertThat(parsedAcknowledgement).isEqualTo(acknowledgement);
    }

    @Test
    public void parseJsonRepresentationWithoutLabel() {
        final Acknowledgement acknowledgement =
                Acknowledgement.of(KNOWN_LABEL, KNOWN_ENTITY_ID, HttpStatus.OK, dittoHeaders);
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
                Acknowledgement.of(KNOWN_LABEL, KNOWN_ENTITY_ID, HttpStatus.OK, dittoHeaders);
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
                Acknowledgement.of(KNOWN_LABEL, KNOWN_ENTITY_ID, HttpStatus.OK, dittoHeaders);
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
    public void parseJsonRepresentationWithoutEntityType() {
        final Acknowledgement acknowledgement =
                Acknowledgement.of(KNOWN_LABEL, KNOWN_ENTITY_ID, HttpStatus.OK, dittoHeaders);
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
    public void parseJsonRepresentationWithoutStatusCode() {
        final Acknowledgement acknowledgement =
                Acknowledgement.of(KNOWN_LABEL, KNOWN_ENTITY_ID, HttpStatus.OK, dittoHeaders);
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
                Acknowledgement.of(KNOWN_LABEL, KNOWN_ENTITY_ID, HttpStatus.OK, dittoHeaders);
        final JsonFieldDefinition<Integer> statusCodeFieldDefinition = Acknowledgement.JsonFields.STATUS_CODE;
        final int unknownStatusCode = 19;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgement.toJson())
                .set(statusCodeFieldDefinition, unknownStatusCode)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withCauseInstanceOf(HttpStatusCodeOutOfRangeException.class);
    }

    @Test
    public void parseJsonRepresentationWithoutDittoHeaders() {
        final Acknowledgement acknowledgement =
                Acknowledgement.of(KNOWN_LABEL, KNOWN_ENTITY_ID, HttpStatus.OK, dittoHeaders);
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
                Acknowledgement.of(KNOWN_LABEL, KNOWN_ENTITY_ID, HttpStatus.OK, dittoHeaders);
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
