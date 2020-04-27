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
package org.eclipse.ditto.signals.acks.things;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.assertj.core.util.Lists;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
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
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final ThingId thingId = ThingId.generateRandom();
        knownAcknowledgement =
                Acknowledgement.of(AcknowledgementLabel.of("foo"), thingId, HttpStatusCode.OK, dittoHeaders);
        final Acknowledgement knownAcknowledgement2 =
                Acknowledgement.of(AcknowledgementLabel.of("bar"), thingId, HttpStatusCode.NOT_FOUND, dittoHeaders,
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
            softly.assertThat(acknowledgements.getType())
                    .as("same type")
                    .isEqualTo(Acknowledgements.getType(knownAcknowledgement.getEntityType()));
            softly.assertThat(acknowledgements.getStatusCode())
                    .as("same status code")
                    .isEqualTo(HttpStatusCode.FAILED_DEPENDENCY);
            softly.assertThat(acknowledgements.getDittoHeaders())
                    .as("same DittoHeaders")
                    .isEqualTo(dittoHeaders);
        }
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Acknowledgements acknowledgements =
                ThingAcknowledgementsFactory.newAcknowledgements(acknowledgementList, dittoHeaders);

        final Acknowledgements parsedAcknowledgements =
                ThingAcknowledgementsFactory.fromJson(acknowledgements.toJson());

        assertThat(parsedAcknowledgements).isEqualTo(acknowledgements);
    }

}
