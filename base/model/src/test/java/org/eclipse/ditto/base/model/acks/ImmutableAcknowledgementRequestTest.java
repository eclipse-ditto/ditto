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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.acks.ImmutableAcknowledgementRequest}.
 */
public final class ImmutableAcknowledgementRequestTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAcknowledgementRequest.class,
                areImmutable(),
                provided(AcknowledgementLabel.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAcknowledgementRequest.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullAckLabel() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableAcknowledgementRequest.getInstance(null))
                .withMessage("The acknowledgementLabel must not be null!")
                .withNoCause();
    }

    @Test
    public void getLabelReturnsExpected() {
        final AcknowledgementLabel label = DittoAcknowledgementLabel.TWIN_PERSISTED;
        final ImmutableAcknowledgementRequest underTest = ImmutableAcknowledgementRequest.getInstance(label);

        assertThat((CharSequence) underTest.getLabel()).isEqualTo(label);
    }

    @Test
    public void toStringReturnsExpected() {
        final AcknowledgementLabel label = DittoAcknowledgementLabel.TWIN_PERSISTED;
        final ImmutableAcknowledgementRequest underTest = ImmutableAcknowledgementRequest.getInstance(label);

        assertThat(underTest.toString()).hasToString(label.toString());
    }

}
