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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import akka.kafka.ConsumerMessage;
import nl.jqno.equalsverifier.EqualsVerifier;


public final class CommittableTransformationResultTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(CommittableTransformationResult.class, areImmutable(),
                provided(TransformationResult.class, ConsumerMessage.CommittableOffset.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CommittableTransformationResult.class).verify();
    }

    @Test
    public void getTransformationResultReturnsSame() {
        final TransformationResult transformationResult = mock(TransformationResult.class);
        final ConsumerMessage.CommittableOffset committableOffset = mock(ConsumerMessage.CommittableOffset.class);
        final CommittableTransformationResult result =
                CommittableTransformationResult.of(transformationResult, committableOffset);

        assertThat(result.getTransformationResult()).isSameAs(transformationResult);
    }

    @Test
    public void getCommittableOffsetReturnsSame() {
        final TransformationResult transformationResult = mock(TransformationResult.class);
        final ConsumerMessage.CommittableOffset committableOffset = mock(ConsumerMessage.CommittableOffset.class);
        final CommittableTransformationResult result =
                CommittableTransformationResult.of(transformationResult, committableOffset);

        assertThat(result.getCommittableOffset()).isSameAs(committableOffset);
    }

}
