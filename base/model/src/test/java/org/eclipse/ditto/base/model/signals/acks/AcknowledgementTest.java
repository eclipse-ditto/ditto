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
package org.eclipse.ditto.base.model.signals.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link Acknowledgement}.
 */
public final class AcknowledgementTest {

    private static final AcknowledgementLabel KNOWN_ACK_LABEL = AcknowledgementLabel.of("welcome-ack");
    private static final EntityId KNOWN_ENTITY_ID = EntityId.of(EntityType.of("thing"), UUID.randomUUID().toString());
    private static final HttpStatus KNOWN_STATUS_CODE = HttpStatus.OK;
    private static final JsonValue KNOWN_PAYLOAD = JsonObject.newBuilder().set("known", "payload").build();

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .responseRequired(false)
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(Acknowledgement.class, areImmutable(),
                provided(AcknowledgementLabel.class,
                        EntityId.class,
                        EntityType.class,
                        DittoHeaders.class,
                        JsonValue.class).areAlsoImmutable(),
                provided("T").isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        final EntityId red = EntityId.of(EntityType.of("thing"), UUID.randomUUID().toString());
        final EntityId black = EntityId.of(EntityType.of("thing"), UUID.randomUUID().toString());

        EqualsVerifier.forClass(Acknowledgement.class)
                .usingGetClass()
                .withPrefabValues(EntityId.class, red, black)
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullAcknowledgementLabel() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> Acknowledgement.of(null, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders, null))
                .withMessage("The label must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullEntityId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> Acknowledgement.of(KNOWN_ACK_LABEL, null, KNOWN_STATUS_CODE, dittoHeaders, null))
                .withMessage("The entityId must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullHttpStatus() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, null, dittoHeaders, null))
                .withMessage("The httpStatus must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullDittoHeaders() {
        assertThatNullPointerException()
                .isThrownBy(() -> Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, null,
                        null))
                .withMessage("The dittoHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void toJsonWithoutEntityReturnsExpected() {
        final JsonObject expected = JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE, Acknowledgement.TYPE)
                .set(Acknowledgement.JsonFields.LABEL, KNOWN_ACK_LABEL.toString())
                .set(Acknowledgement.JsonFields.ENTITY_ID, KNOWN_ENTITY_ID.toString())
                .set(Acknowledgement.JsonFields.ENTITY_TYPE, KNOWN_ENTITY_ID.getEntityType().toString())
                .set(Acknowledgement.JsonFields.STATUS_CODE, KNOWN_STATUS_CODE.getCode())
                .set(Acknowledgement.JsonFields.DITTO_HEADERS, dittoHeaders.toJson())
                .build();

        final DittoHeaders dittoHeadersWithAckRequests = this.dittoHeaders.toBuilder()
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("foo:bar")))
                .build();

        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE,
                        dittoHeadersWithAckRequests, null);

        assertThat(underTest.toJson()).isEqualTo(expected);
    }

    @Test
    public void toJsonWithEntityReturnsExpected() {
        final JsonObject expected = JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE, Acknowledgement.TYPE)
                .set(Acknowledgement.JsonFields.LABEL, KNOWN_ACK_LABEL.toString())
                .set(Acknowledgement.JsonFields.ENTITY_ID, KNOWN_ENTITY_ID.toString())
                .set(Acknowledgement.JsonFields.ENTITY_TYPE, KNOWN_ENTITY_ID.getEntityType().toString())
                .set(Acknowledgement.JsonFields.STATUS_CODE, KNOWN_STATUS_CODE.getCode())
                .set(Acknowledgement.JsonFields.PAYLOAD, KNOWN_PAYLOAD)
                .set(Acknowledgement.JsonFields.DITTO_HEADERS, dittoHeaders.toJson())
                .build();

        final DittoHeaders dittoHeadersWithAckRequests = dittoHeaders.toBuilder()
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("foo:bar")))
                .build();

        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE,
                        dittoHeadersWithAckRequests, KNOWN_PAYLOAD);

        assertThat(underTest.toJson()).isEqualTo(expected);
    }

    @Test
    public void getLabelReturnsExpected() {
        final AcknowledgementLabel label = KNOWN_ACK_LABEL;
        final Acknowledgement underTest =
                Acknowledgement.of(label, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders, KNOWN_PAYLOAD);

        assertThat((CharSequence) underTest.getLabel()).isEqualTo(label);
    }

    @Test
    public void getEntityIdReturnsExpected() {
        final EntityId entityId = KNOWN_ENTITY_ID;
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, entityId, KNOWN_STATUS_CODE, dittoHeaders, KNOWN_PAYLOAD);

        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(entityId);
    }

    @Test
    public void acknowledgementWithHttpStatusNotFoundIsNotSuccess() {
        final HttpStatus httpStatus = HttpStatus.NOT_FOUND;
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, httpStatus, dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.isSuccess()).isFalse();
    }

    @Test
    public void acknowledgementWithHttpStatusOkIsSuccess() {
        final HttpStatus httpStatus = HttpStatus.OK;
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, httpStatus, dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.isSuccess()).isTrue();
    }

    @Test
    public void acknowledgementWithHttpStatusOkIsNotTimeout() {
        final HttpStatus httpStatus = HttpStatus.OK;
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, httpStatus, dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.isTimeout()).isFalse();
    }

    @Test
    public void acknowledgementWithHttpStatusRequestTimeoutIsTimeout() {
        final HttpStatus httpStatus = HttpStatus.REQUEST_TIMEOUT;
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, httpStatus, dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.isTimeout()).isTrue();
    }

    @Test
    public void getHttpStatusReturnsExpected() {
        final HttpStatus httpStatus = KNOWN_STATUS_CODE;
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, httpStatus, dittoHeaders, KNOWN_PAYLOAD);

        assertThat(underTest.getHttpStatus()).isEqualTo(httpStatus);
    }

    @Test
    public void getExistingEntity() {
        final JsonValue payload = KNOWN_PAYLOAD;
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders, payload);

        assertThat(underTest.getEntity()).contains(payload);
    }

    @Test
    public void getMissingEntity() {
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders, null);

        assertThat(underTest.getEntity()).isEmpty();
    }

    @Test
    public void getDittoHeadersReturnsExpected() {
        final DittoHeaders dittoHeadersWithAckRequests = this.dittoHeaders.toBuilder()
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("foo:bar")))
                .build();
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE,
                        dittoHeadersWithAckRequests, KNOWN_PAYLOAD);

        assertThat(underTest.getDittoHeaders()).isEqualTo(this.dittoHeaders);
    }

    @Test
    public void responseRequiredIsSetToFalse() {
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, DittoHeaders.empty(),
                        KNOWN_PAYLOAD);

        assertThat(underTest.getDittoHeaders().isResponseRequired()).isFalse();
    }

    @Test
    public void setDittoHeadersWorksAsExpected() {
        final DittoHeaders newDittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().
                responseRequired(false)
                .build();
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders,
                        KNOWN_PAYLOAD);

        final Acknowledgement newAcknowledgement = underTest.setDittoHeaders(newDittoHeaders);

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
            softly.assertThat(newAcknowledgement.getHttpStatus())
                    .as("same HTTP status")
                    .isEqualTo(underTest.getHttpStatus());
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
        final Acknowledgement underTest =
                Acknowledgement.of(KNOWN_ACK_LABEL, KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, dittoHeaders,
                        KNOWN_PAYLOAD);

        assertThat((CharSequence) underTest.getEntityType()).isEqualTo(entityType);
    }

    @Test
    public void isWeakWhenHeaderIsTrue() {
        final DittoHeaders ackHeader = dittoHeaders.toBuilder()
                .putHeader(DittoHeaderDefinition.WEAK_ACK.getKey(), "true")
                .build();
        final Acknowledgement underTest = Acknowledgement.of(KNOWN_ACK_LABEL,
                KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, ackHeader, KNOWN_PAYLOAD);

        assertThat(underTest.isWeak()).isTrue();
    }

    @Test
    public void isNotWeakWhenHeaderIsFalse() {
        final DittoHeaders ackHeader = dittoHeaders
                .toBuilder()
                .putHeader(DittoHeaderDefinition.WEAK_ACK.getKey(), "false")
                .build();
        final Acknowledgement underTest = Acknowledgement.of(KNOWN_ACK_LABEL,
                KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, ackHeader, KNOWN_PAYLOAD);

        assertThat(underTest.isWeak()).isFalse();
    }

    @Test
    public void isNotWeakWhenHeaderIsNotPresent() {
        final DittoHeaders ackHeader = dittoHeaders.toBuilder()
                .removeHeader(DittoHeaderDefinition.WEAK_ACK.getKey())
                .build();
        final Acknowledgement underTest = Acknowledgement.of(KNOWN_ACK_LABEL,
                KNOWN_ENTITY_ID, KNOWN_STATUS_CODE, ackHeader, KNOWN_PAYLOAD);

        assertThat(underTest.isWeak()).isFalse();
    }

}
