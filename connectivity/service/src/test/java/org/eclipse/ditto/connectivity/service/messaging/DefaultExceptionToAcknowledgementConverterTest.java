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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Unit test for {@link DefaultExceptionToAcknowledgementConverter}.
 */
public final class DefaultExceptionToAcknowledgementConverterTest {

    private static final AcknowledgementLabel ACK_LABEL = AcknowledgementLabel.of("twin-persisted");
    private static final EntityId ENTITY_ID = ThingId.generateRandom();

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;
    private DefaultExceptionToAcknowledgementConverter underTest;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        underTest = DefaultExceptionToAcknowledgementConverter.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultExceptionToAcknowledgementConverter.class, areImmutable());
    }

    @Test
    public void getInstanceCreatesSingleton() {
        final DefaultExceptionToAcknowledgementConverter firstInstance =
                DefaultExceptionToAcknowledgementConverter.getInstance();
        final DefaultExceptionToAcknowledgementConverter secondInstance =
                DefaultExceptionToAcknowledgementConverter.getInstance();

        assertThat(firstInstance).isSameAs(secondInstance);
    }

    @Test
    public void convertExceptionWithNullException() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.convertException(null, ACK_LABEL, ENTITY_ID, dittoHeaders))
                .withMessage("The exception must not be null!")
                .withNoCause();
    }

    @Test
    public void convertExceptionWithNullLabel() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.convertException(new RuntimeException(), null, ENTITY_ID, dittoHeaders))
                .withMessage("The label must not be null!")
                .withNoCause();
    }

    @Test
    public void convertExceptionWithNullEntityId() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.convertException(new RuntimeException(), ACK_LABEL, null, dittoHeaders))
                .withMessage("The entityId must not be null!")
                .withNoCause();
    }

    @Test
    public void convertExceptionWithNullDittoHeaders() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.convertException(new RuntimeException(), ACK_LABEL, ENTITY_ID, null))
                .withMessage("The dittoHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void convertGenericExceptionWithMessageWithoutCause() {
        final String exceptionMessage = "A foo that went bar!";
        final IllegalStateException exception = new IllegalStateException(exceptionMessage);
        final JsonObject expectedPayload = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.MESSAGE, getGenericExceptionMessage(exception))
                .set(DittoRuntimeException.JsonFields.DESCRIPTION, exceptionMessage)
                .build();
        final Acknowledgement expected =
                Acknowledgement.of(ACK_LABEL, ENTITY_ID, HttpStatus.INTERNAL_SERVER_ERROR, dittoHeaders,
                        expectedPayload);

        final Acknowledgement actual = underTest.convertException(exception, ACK_LABEL, ENTITY_ID, dittoHeaders);

        assertThat(actual).isEqualTo(expected);
    }

    private static String getGenericExceptionMessage(final Exception exception) {
        return "Encountered <" + exception.getClass().getSimpleName() + ">.";
    }

    @Test
    public void convertGenericExceptionWithMessageWithCause() {
        final String exceptionMessage = "A foo that went bar!";
        final NullPointerException cause = new NullPointerException();
        final IllegalStateException exception = new IllegalStateException(exceptionMessage, cause);
        final JsonObject expectedPayload = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.MESSAGE, getGenericExceptionMessage(exception))
                .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                        getGenericExceptionDescriptionWithCause(exception, exception.getMessage()))
                .build();
        final Acknowledgement expected =
                Acknowledgement.of(ACK_LABEL, ENTITY_ID, HttpStatus.INTERNAL_SERVER_ERROR, dittoHeaders,
                        expectedPayload);

        final Acknowledgement actual = underTest.convertException(exception, ACK_LABEL, ENTITY_ID, dittoHeaders);

        assertThat(actual).isEqualTo(expected);
    }

    private static String getGenericExceptionDescriptionWithCause(final Exception exception, final String message) {
        final Throwable cause = exception.getCause();
        return MessageFormat.format("{0} - Caused by <{1}>.", message, cause.getClass().getSimpleName());
    }

    @Test
    public void convertGenericExceptionWithoutMessageWithoutCause() {
        final IllegalStateException exception = new IllegalStateException();
        final JsonObject expectedPayload = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.MESSAGE, getGenericExceptionMessage(exception))
                .set(DittoRuntimeException.JsonFields.DESCRIPTION, "Unknown error.")
                .build();
        final Acknowledgement expected =
                Acknowledgement.of(ACK_LABEL, ENTITY_ID, HttpStatus.INTERNAL_SERVER_ERROR, dittoHeaders,
                        expectedPayload);

        final Acknowledgement actual = underTest.convertException(exception, ACK_LABEL, ENTITY_ID, dittoHeaders);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void convertGenericExceptionWithoutMessageWithCause() {
        final NullPointerException cause = new NullPointerException();
        final IllegalStateException exception = new IllegalStateException(cause);
        final JsonObject expectedPayload = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.MESSAGE, getGenericExceptionMessage(exception))
                .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                        getGenericExceptionDescriptionWithCause(exception, cause.toString()))
                .build();
        final Acknowledgement expected =
                Acknowledgement.of(ACK_LABEL, ENTITY_ID, HttpStatus.INTERNAL_SERVER_ERROR, dittoHeaders,
                        expectedPayload);

        final Acknowledgement actual = underTest.convertException(exception, ACK_LABEL, ENTITY_ID, dittoHeaders);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void convertDittoRuntimeException() {
        final ConnectionConfigurationInvalidException exception =
                ConnectionConfigurationInvalidException.newBuilder("A foo cannot be bar!").build();
        final JsonObject expectedPayload = JsonFactory.newObjectBuilder(exception.toJson())
                .remove(DittoRuntimeException.JsonFields.STATUS)
                .build();
        final Acknowledgement expected =
                Acknowledgement.of(ACK_LABEL, ENTITY_ID, exception.getHttpStatus(), dittoHeaders, expectedPayload);

        final Acknowledgement actual = underTest.convertException(exception, ACK_LABEL, ENTITY_ID, dittoHeaders);

        assertThat(actual).isEqualTo(expected);
    }

}
