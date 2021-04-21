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
package org.eclipse.ditto.signals.acks.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.common.HttpStatusCodeOutOfRangeException;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
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
    private AcknowledgementsJsonParser<EntityId> underTest;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final EntityId entityId = EntityId.of(EntityType.of("thing"), UUID.randomUUID().toString());
        knownAcknowledgement =
                Acknowledgement.of(AcknowledgementLabel.of("foo"), entityId, HttpStatus.OK, dittoHeaders);
        final Acknowledgement knownAcknowledgement2 =
                Acknowledgement.of(AcknowledgementLabel.of("bar"), entityId, HttpStatus.NOT_FOUND, dittoHeaders,
                        JsonValue.of("bar does not exist!"));
        knownAcknowledgements =
                Acknowledgements.of(Sets.newSet(knownAcknowledgement, knownAcknowledgement2), dittoHeaders);

        underTest = AcknowledgementsJsonParser.getInstance(new EntityIdAcknowledgementJsonParser());
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
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The jsonObject must not be null!")
                .withNoCause();
    }

    @Test
    public void parseJsonWithSeveralAcksOfSameLabel() {
        final AcknowledgementLabel label = AcknowledgementLabel.of("same-label");
        final Acknowledgements acks = Acknowledgements.of(Arrays.asList(
                Acknowledgement.of(label, EntityId.of(EntityType.of("thing"), "test:thing-id"), HttpStatus.OK,
                        DittoHeaders.empty()),
                Acknowledgement.of(label, EntityId.of(EntityType.of("thing"), "test:thing-id"), HttpStatus.FORBIDDEN,
                        DittoHeaders.empty())
        ), DittoHeaders.empty());

        final Acknowledgements parsedAcknowledgements = underTest.apply(acks.toJson());

        assertThat(parsedAcknowledgements).isEqualTo(acks);
    }

    @Test
    public void parseValidJsonRepresentationOfNonEmptyAcknowledgements() {
        final JsonObject jsonRepresentation = knownAcknowledgements.toJson();

        final Acknowledgements parsedAcknowledgements = underTest.apply(jsonRepresentation);

        assertThat(parsedAcknowledgements).isEqualTo(knownAcknowledgements);
    }

    @Test
    public void parseValidJsonRepresentationOfEmptyAcknowledgements() {
        final EntityId entityId = knownAcknowledgements.getEntityId();
        final Acknowledgements acknowledgements = Acknowledgements.empty(entityId, dittoHeaders);

        final Acknowledgements parsedAcknowledgements = underTest.apply(acknowledgements.toJson());

        assertThat(parsedAcknowledgements).isEqualTo(acknowledgements);
    }

    @Test
    public void parseJsonRepresentationWithoutEntityId() {
        final JsonFieldDefinition<String> entityIdFieldDefinition = Acknowledgements.JsonFields.ENTITY_ID;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .remove(entityIdFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
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
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
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
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessage("Value <%s> for <%s> is not of type <String>!", invalidEntityType,
                        entityTypeFieldDefinition.getPointer())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithUnexpectedEntityType() {
        final JsonFieldDefinition<String> entityTypeFieldDefinition = Acknowledgements.JsonFields.ENTITY_TYPE;
        final EntityType unexpectedEntityType = EntityType.of("plumbus");
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .set(entityTypeFieldDefinition, unexpectedEntityType.toString())
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessage("The read entity type <%s> differs from the expected <%s>!", unexpectedEntityType,
                        knownAcknowledgements.getEntityType())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithoutDittoHeaders() {
        final JsonFieldDefinition<?> dittoHeadersFieldDefinition = Acknowledgements.JsonFields.DITTO_HEADERS;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .remove(dittoHeadersFieldDefinition)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessageContaining(dittoHeadersFieldDefinition.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void parseJsonRepresentationWithInvalidDittoHeaders() {
        final JsonValue invalidDittoHeaders = JsonValue.of("dittoHeaders");
        final JsonFieldDefinition<?> dittoHeadersFieldDefinition = Acknowledgements.JsonFields.DITTO_HEADERS;
        final JsonObject jsonRepresentation = JsonFactory.newObjectBuilder(knownAcknowledgements.toJson())
                .set(dittoHeadersFieldDefinition.getPointer(), invalidDittoHeaders)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessage("Value <%s> for <%s> is not of type <JsonObject>!", invalidDittoHeaders,
                        dittoHeadersFieldDefinition.getPointer())
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
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
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
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
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
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
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
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
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
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
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
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
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
                .isThrownBy(() -> underTest.apply(jsonRepresentation))
                .withMessage("The read status code <%d> differs from the expected <%d>!",
                        unexpectedStatusCode.getCode(), knownAcknowledgements.getHttpStatus().getCode())
                .withNoCause();
    }

    private static final class EntityIdAcknowledgementJsonParser extends AcknowledgementJsonParser<EntityId> {

        @Override
        protected EntityId createEntityIdInstance(final CharSequence entityIdValue) {
            return EntityId.of(EntityType.of("thing"), entityIdValue);
        }

    }

}
