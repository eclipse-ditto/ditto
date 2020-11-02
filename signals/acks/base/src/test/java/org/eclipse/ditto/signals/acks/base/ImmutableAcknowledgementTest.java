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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableAcknowledgement}.
 */
public final class ImmutableAcknowledgementTest {

    private static final AcknowledgementLabel KNOWN_ACK_LABEL = AcknowledgementLabel.of("welcome-ack");
    private static final ThingId KNOWN_ENTITY_ID = ThingId.generateRandom();
    private static final HttpStatusCode KNOWN_STATUS_CODE = HttpStatusCode.OK;
    private static final JsonValue KNOWN_PAYLOAD = JsonObject.newBuilder().set("known", "payload").build();

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAcknowledgement.class, areImmutable(),
                provided(AcknowledgementLabel.class,
                        EntityId.class,
                        EntityType.class,
                        DittoHeaders.class,
                        JsonValue.class).areAlsoImmutable(),
                provided("T").isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        final ThingId red = ThingId.generateRandom();
        final ThingId black = ThingId.generateRandom();

        EqualsVerifier.forClass(ImmutableAcknowledgement.class)
                .usingGetClass()
                .withPrefabValues(EntityIdWithType.class, red, black)
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullAcknowledgementLabel() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> ImmutableAcknowledgement.of(null, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders, null))
                .withMessage("The label must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullEntityId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, null, KNOWN_STATUS_CODE, dittoHeaders, null))
                .withMessage("The entityId must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullStatusCode() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, null, dittoHeaders, null))
                .withMessage("The statusCode must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullDittoHeaders() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, null,
                        null))
                .withMessage("The dittoHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void toJsonWithoutEntityReturnsExpected() {
        final JsonObject expected = JsonObject.newBuilder()
                .set(Acknowledgement.JsonFields.LABEL, KNOWN_ACK_LABEL.toString())
                .set(Acknowledgement.JsonFields.ENTITY_ID, KNOWN_ENTITY_ID.toString())
                .set(Acknowledgement.JsonFields.ENTITY_TYPE, KNOWN_ENTITY_ID.getEntityType().toString())
                .set(Acknowledgement.JsonFields.STATUS_CODE, KNOWN_STATUS_CODE.toInt())
                .set(Acknowledgement.JsonFields.DITTO_HEADERS, dittoHeaders.toJson())
                .build();
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders, null);

        assertThat(underTest.toJson()).isEqualTo(expected);
    }

    @Test
    public void toJsonWithEntityReturnsExpected() {
        final JsonObject expected = JsonObject.newBuilder()
                .set(Acknowledgement.JsonFields.LABEL, KNOWN_ACK_LABEL.toString())
                .set(Acknowledgement.JsonFields.ENTITY_ID, KNOWN_ENTITY_ID.toString())
                .set(Acknowledgement.JsonFields.ENTITY_TYPE, KNOWN_ENTITY_ID.getEntityType().toString())
                .set(Acknowledgement.JsonFields.STATUS_CODE, KNOWN_STATUS_CODE.toInt())
                .set(Acknowledgement.JsonFields.PAYLOAD, KNOWN_PAYLOAD)
                .set(Acknowledgement.JsonFields.DITTO_HEADERS, dittoHeaders.toJson())
                .build();
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders,
                        KNOWN_PAYLOAD);

        assertThat(underTest.toJson()).isEqualTo(expected);
    }

    @Test
    public void getLabelReturnsExpected() {
        final AcknowledgementLabel label = KNOWN_ACK_LABEL;
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(label, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders, KNOWN_PAYLOAD);

        assertThat((CharSequence) underTest.getLabel()).isEqualTo(label);
    }

    @Test
    public void getEntityIdReturnsExpected() {
        final ThingId entityId = KNOWN_ENTITY_ID;
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, entityId, KNOWN_STATUS_CODE, dittoHeaders, KNOWN_PAYLOAD);

        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(entityId);
    }

    @Test
    public void acknowledgementWithStatusCodeNotFoundIsNotSuccess() {
        final HttpStatusCode statusCode = HttpStatusCode.NOT_FOUND;
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, statusCode, dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.isSuccess()).isFalse();
    }

    @Test
    public void acknowledgementWithStatusCodeOkIsSuccess() {
        final HttpStatusCode statusCode = HttpStatusCode.OK;
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, statusCode, dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.isSuccess()).isTrue();
    }

    @Test
    public void liveResponseAcknowledgementWithNonTimeoutIsSuccess() {
        final HttpStatusCode statusCode = HttpStatusCode.NOT_FOUND;
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(DittoAcknowledgementLabel.LIVE_RESPONSE, KNOWN_ENTITY_ID, statusCode,
                        dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.isSuccess()).isTrue();
    }

    @Test
    public void liveResponseAcknowledgementWithTimeoutIsFailed() {
        final HttpStatusCode statusCode = HttpStatusCode.REQUEST_TIMEOUT;
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(DittoAcknowledgementLabel.LIVE_RESPONSE, KNOWN_ENTITY_ID, statusCode,
                        dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.isSuccess()).isFalse();
    }

    @Test
    public void acknowledgementWithStatusCodeOkIsNotTimeout() {
        final HttpStatusCode statusCode = HttpStatusCode.OK;
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, statusCode, dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.isTimeout()).isFalse();
    }

    @Test
    public void acknowledgementWithStatusCodeRequestTimeoutIsTimeout() {
        final HttpStatusCode statusCode = HttpStatusCode.REQUEST_TIMEOUT;
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, statusCode, dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.isTimeout()).isTrue();
    }

    @Test
    public void getStatusCodeReturnsExpected() {
        final HttpStatusCode statusCode = KNOWN_STATUS_CODE;
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, statusCode, dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.getStatusCode()).isEqualTo(statusCode);
    }

    @Test
    public void getExistingEntity() {
        final JsonValue payload = KNOWN_PAYLOAD;
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders, payload);

        assertThat(underTest.getEntity()).contains(payload);
    }

    @Test
    public void getMissingEntity() {
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders, null);

        assertThat(underTest.getEntity()).isEmpty();
    }

    @Test
    public void getDittoHeadersReturnsExpected() {
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders,
                        KNOWN_PAYLOAD);

        assertThat(underTest.getDittoHeaders()).isEqualTo(dittoHeaders);
    }

    @Test
    public void setDittoHeadersWorksAsExpected() {
        final DittoHeaders newDittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders,
                        KNOWN_PAYLOAD);

        final ImmutableAcknowledgement<ThingId> newAcknowledgement = underTest.setDittoHeaders(newDittoHeaders);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(newAcknowledgement)
                    .as("is not same")
                    .isNotSameAs(underTest);
            softly.assertThat(newAcknowledgement.getDittoHeaders())
                    .as("new DittoHeaders")
                    .isEqualTo(newDittoHeaders);
            softly.assertThat((CharSequence) newAcknowledgement.getLabel())
                    .as("same label")
                    .isEqualTo(underTest.getLabel());
            softly.assertThat((CharSequence) newAcknowledgement.getEntityId())
                    .as("same entity ID")
                    .isEqualTo(underTest.getEntityId());
            softly.assertThat(newAcknowledgement.isSuccess())
                    .as("indicates same success")
                    .isEqualTo(underTest.isSuccess());
            softly.assertThat(newAcknowledgement.isTimeout())
                    .as("indicates same timeout")
                    .isEqualTo(underTest.isTimeout());
            softly.assertThat(newAcknowledgement.getStatusCode())
                    .as("same status code")
                    .isEqualTo(underTest.getStatusCode());
            softly.assertThat(newAcknowledgement.getEntity())
                    .as("same entity")
                    .isEqualTo(underTest.getEntity());
            softly.assertThat((CharSequence) newAcknowledgement.getEntityType())
                    .as("same entity type")
                    .isEqualTo(underTest.getEntityType());
        }
    }

    @Test
    public void getEntityTypeReturnsExpected() {
        final EntityType entityType = KNOWN_ENTITY_ID.getEntityType();
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders,
                        KNOWN_PAYLOAD);

        assertThat((CharSequence) underTest.getEntityType()).isEqualTo(entityType);
    }

    @Test
    public void getTypeReturnsExpected() {
        final String expected = Acknowledgement.getType(KNOWN_ENTITY_ID.getEntityType());
        final ImmutableAcknowledgement<ThingId> underTest =
                ImmutableAcknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders,
                        KNOWN_PAYLOAD);

        assertThat(underTest.getType()).isEqualTo(expected);
    }

}
