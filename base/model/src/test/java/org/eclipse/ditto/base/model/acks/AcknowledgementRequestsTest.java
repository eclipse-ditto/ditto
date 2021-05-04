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
package org.eclipse.ditto.base.model.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.acks.AcknowledgementRequests}.
 */
public final class AcknowledgementRequestsTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(AcknowledgementRequests.class, areImmutable());
    }

    @Test
    public void tryToGetNewAcknowledgementRequestForNullLabel() {
        assertThatNullPointerException()
                .isThrownBy(() -> AcknowledgementRequests.newAcknowledgementRequest(null))
                .withMessage("The acknowledgementLabel must not be null!")
                .withNoCause();
    }

    @Test
    public void getNewAcknowledgementRequestReturnsExpected() {
        final AcknowledgementLabel ackLabel = DittoAcknowledgementLabel.TWIN_PERSISTED;
        final ImmutableAcknowledgementRequest expected = ImmutableAcknowledgementRequest.getInstance(ackLabel);

        final ImmutableAcknowledgementRequest actual = AcknowledgementRequests.newAcknowledgementRequest(ackLabel);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void tryToParseAcknowledgementRequestFromNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> AcknowledgementRequests.parseAcknowledgementRequest(null))
                .withMessage("The ackRequestRepresentation must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToParseAcknowledgementRequestFromStringWithInvalidLabel() {
        final String invalidAckLabel = "ab";
        final AcknowledgementLabelInvalidException expectedCause =
                new AcknowledgementLabelInvalidException(invalidAckLabel);

        assertThatExceptionOfType(AcknowledgementRequestParseException.class)
                .isThrownBy(() -> AcknowledgementRequests.parseAcknowledgementRequest(invalidAckLabel))
                .withMessageContaining(invalidAckLabel)
                .withMessageContaining(expectedCause.getMessage())
                .withCause(expectedCause);
    }

    @Test
    public void parseAcknowledgementRequestFromValidStringRepresentation() {
        final AcknowledgementLabel ackLabel = DittoAcknowledgementLabel.TWIN_PERSISTED;
        final ImmutableAcknowledgementRequest expected = ImmutableAcknowledgementRequest.getInstance(ackLabel);

        final ImmutableAcknowledgementRequest actual = AcknowledgementRequests.parseAcknowledgementRequest(ackLabel);

        assertThat(actual).isEqualTo(expected);
    }

}
