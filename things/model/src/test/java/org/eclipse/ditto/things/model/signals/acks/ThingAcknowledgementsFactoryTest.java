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
package org.eclipse.ditto.things.model.signals.acks;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.assertj.core.util.Lists;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Unit test for {@link ThingAcknowledgementsFactory}.
 */
public final class ThingAcknowledgementsFactoryTest {

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;
    private Acknowledgement knownAcknowledgement;
    private List<Acknowledgement> acknowledgementList;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .responseRequired(false)
                .build();
        final ThingId thingId = ThingId.generateRandom();
        knownAcknowledgement =
                Acknowledgement.of(AcknowledgementLabel.of("foo"), thingId, HttpStatus.OK, dittoHeaders);
        final Acknowledgement knownAcknowledgement2 =
                Acknowledgement.of(AcknowledgementLabel.of("bar"), thingId, HttpStatus.NOT_FOUND, dittoHeaders,
                        JsonValue.of("bar does not exist!"));
        acknowledgementList = Lists.list(knownAcknowledgement, knownAcknowledgement2);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingAcknowledgementsFactory.class, areImmutable());
    }

    @Test
    public void newAcknowledgementsReturnsExpected() {
        final Acknowledgements acknowledgements =
                ThingAcknowledgementsFactory.newAcknowledgements(acknowledgementList, dittoHeaders);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(acknowledgements)
                    .as("contains elements")
                    .containsOnlyElementsOf(acknowledgementList);
            softly.assertThat((CharSequence) acknowledgements.getEntityId())
                    .as("same entity ID")
                    .isEqualTo(knownAcknowledgement.getEntityId());
            softly.assertThat(acknowledgements.getHttpStatus())
                    .as("same status code")
                    .isEqualTo(HttpStatus.FAILED_DEPENDENCY);
            softly.assertThat(acknowledgements.getDittoHeaders())
                    .as("same DittoHeaders")
                    .isEqualTo(dittoHeaders);
        }
    }

}
