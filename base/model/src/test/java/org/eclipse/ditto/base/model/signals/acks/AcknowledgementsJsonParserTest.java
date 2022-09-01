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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.UUID;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
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
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.internal.util.collections.Sets;

/**
 * Unit test for {@link AcknowledgementsJsonParser}.
 */
public final class AcknowledgementsJsonParserTest {

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;
    private Acknowledgement knownAcknowledgement;
    private Acknowledgements knownAcknowledgements;
    private AcknowledgementsJsonParser underTest;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final EntityId entityId = EntityId.of(EntityType.of("thing"), "namespace:" + UUID.randomUUID().toString());
        knownAcknowledgement =
                Acknowledgement.of(AcknowledgementLabel.of("foo"), entityId, HttpStatus.OK, dittoHeaders);
        final Acknowledgement knownAcknowledgement2 =
                Acknowledgement.of(AcknowledgementLabel.of("bar"), entityId, HttpStatus.NOT_FOUND, dittoHeaders,
                        JsonValue.of("bar does not exist!"));
        knownAcknowledgements =
                Acknowledgements.of(Sets.newSet(knownAcknowledgement, knownAcknowledgement2), dittoHeaders);

        underTest = AcknowledgementsJsonParser.getInstance(new AcknowledgementJsonParser());
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(AcknowledgementsJsonParser.class,
                areImmutable(),
                provided(AcknowledgementJsonParser.class).isAlsoImmutable());
    }

    @Test
    public void getInstanceWithNullAcknowledgementJsonParser() {
        assertThatNullPointerException()
                .isThrownBy(() -> AcknowledgementsJsonParser.getInstance(null))
                .withMessage("The acknowledgementJsonParser must not be null!")
                .withNoCause();
    }

    @Test
    public void parseNullJsonObject() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.apply(null, DittoHeaders.empty()))
                .withMessage("The jsonObject must not be null!")
                .withNoCause();
    }

    @Test
    public void parseJsonWithSeveralAcksOfSameLabel() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final AcknowledgementLabel label = AcknowledgementLabel.of("same-label");
        final Acknowledgements acks = Acknowledgements.of(Arrays.asList(
                Acknowledgement.of(label, EntityId.of(EntityType.of("thing"), "test:thing-id"), HttpStatus.OK,
                        dittoHeaders),
                Acknowledgement.of(label, EntityId.of(EntityType.of("thing"), "test:thing-id"), HttpStatus.FORBIDDEN,
                        dittoHeaders)
        ), dittoHeaders);

        final Acknowledgements parsedAcknowledgements = underTest.apply(acks.toJson(), dittoHeaders);

        assertThat(parsedAcknowledgements).isEqualTo(acks);
    }

    @Test
    public void parseValidJsonRepresentationOfNonEmptyAcknowledgements() {
        final DittoHeaders dittoHeaders = knownAcknowledgements.getDittoHeaders();
        final JsonObject jsonRepresentation = knownAcknowledgements.toJson();

        final Acknowledgements parsedAcknowledgements = underTest.apply(jsonRepresentation, dittoHeaders);

        assertThat(parsedAcknowledgements).isEqualTo(knownAcknowledgements);
    }

    @Test
    public void parseValidJsonRepresentationOfEmptyAcknowledgements() {
        final EntityId entityId = knownAcknowledgements.getEntityId();
        final Acknowledgements acknowledgements = Acknowledgements.empty(entityId, dittoHeaders);

        final Acknowledgements parsedAcknowledgements = underTest.apply(acknowledgements.toJson(), dittoHeaders);

        assertThat(parsedAcknowledgements).isEqualTo(acknowledgements);
    }

    @Test
    public void parseJsonRepresentationWithoutEntityId() {
        final JsonFieldDefinition<String> entityIdFieldDefinition = Acknowledgements.JsonFields.ENTITY_ID;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .remove(entityIdFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation, dittoHeaders))
                .withMessageContaining(entityIdFieldDefinition.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithoutEntityType() {
        final JsonFieldDefinition<String> entityTypeFieldDefinition = Acknowledgements.JsonFields.ENTITY_TYPE;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .remove(entityTypeFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation, dittoHeaders))
                .withMessageContaining(entityTypeFieldDefinition.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithInvalidEntityType() {
        final JsonFieldDefinition<?> entityTypeFieldDefinition = Acknowledgements.JsonFields.ENTITY_TYPE;
        final boolean invalidEntityType = true;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .set(entityTypeFieldDefinition.getPointer(), invalidEntityType)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation, dittoHeaders))
                .withMessage("Value <%s> for <%s> is not of type <String>!", invalidEntityType,
                        entityTypeFieldDefinition.getPointer())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithoutAcknowledgements() {
        final JsonFieldDefinition<JsonObject> acknowledgementsFieldDefinition =
                Acknowledgements.JsonFields.ACKNOWLEDGEMENTS;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .remove(acknowledgementsFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation, dittoHeaders))
                .withMessageContaining(acknowledgementsFieldDefinition.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithInvalidAcknowledgements() {
        final JsonFieldDefinition<JsonObject> acknowledgementsFieldDefinition =
                Acknowledgements.JsonFields.ACKNOWLEDGEMENTS;
        final boolean invalidAcknowledgements = true;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .set(acknowledgementsFieldDefinition.getPointer(), invalidAcknowledgements)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation, dittoHeaders))
                .withMessage("Value <%s> for <%s> is not of type <JsonObject>!", invalidAcknowledgements,
                        acknowledgementsFieldDefinition.getPointer())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithInvalidAcknowledgementJsonRepresentation() {
        final Acknowledgements acknowledgements = Acknowledgements.of(Sets.newSet(knownAcknowledgement), dittoHeaders);
        final JsonFieldDefinition<JsonObject> acknowledgementsFieldDefinition =
                Acknowledgements.JsonFields.ACKNOWLEDGEMENTS;
        final int invalidAcknowledgement = 42;
        final JsonObject invalidAcknowledgements = JsonObject.newBuilder()
                .set(knownAcknowledgement.getLabel(), invalidAcknowledgement)
                .build();
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(acknowledgements.toJson())
                .set(acknowledgementsFieldDefinition, invalidAcknowledgements)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation, dittoHeaders))
                .withMessage("<%d> is not an Acknowledgement JSON object representation!", invalidAcknowledgement)
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithDifferentAcknowledgementEntityId() {
        final EntityId differentEntityId = EntityId.of(EntityType.of("thing"), UUID.randomUUID().toString());
        final Acknowledgement ackWithDifferentEntityId =
                Acknowledgement.of(AcknowledgementLabel.of("baz"), differentEntityId, HttpStatus.OK, dittoHeaders);
        final JsonFieldDefinition<JsonObject> acknowledgementsFieldDefinition =
                Acknowledgements.JsonFields.ACKNOWLEDGEMENTS;
        final JsonPointer ackWithDifferentEntityIdJsonPointer = acknowledgementsFieldDefinition.getPointer()
                .append(JsonPointer.of(ackWithDifferentEntityId.getLabel()));
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .set(ackWithDifferentEntityIdJsonPointer, ackWithDifferentEntityId.toJson())
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation, dittoHeaders))
                .withMessage("The entity ID <%s> of parsed acknowledgement <%s> differs from the expected <%s>!",
                        differentEntityId, ackWithDifferentEntityId, knownAcknowledgements.getEntityId())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithoutStatusCode() {
        final JsonFieldDefinition<Integer> statusCodeFieldDefinition = Acknowledgements.JsonFields.STATUS_CODE;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .remove(statusCodeFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation, dittoHeaders))
                .withMessageContaining(statusCodeFieldDefinition.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithUnknownStatusCode() {
        final JsonFieldDefinition<Integer> statusCodeFieldDefinition = Acknowledgements.JsonFields.STATUS_CODE;
        final int unknownStatusCode = 19;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .set(statusCodeFieldDefinition, unknownStatusCode)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation, dittoHeaders))
                .withCauseInstanceOf(HttpStatusCodeOutOfRangeException.class);
    }

    @Test
    public void parseJsonRepresentationWithUnexpectedStatusCode() {
        final JsonFieldDefinition<Integer> statusCodeFieldDefinition = Acknowledgements.JsonFields.STATUS_CODE;
        final HttpStatus unexpectedStatusCode = HttpStatus.PAYMENT_REQUIRED;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .set(statusCodeFieldDefinition, unexpectedStatusCode.getCode())
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation, dittoHeaders))
                .withMessage("The read status code <%d> differs from the expected <%d>!",
                        unexpectedStatusCode.getCode(), knownAcknowledgements.getHttpStatus().getCode())
                .withNoCause();
    }

}
