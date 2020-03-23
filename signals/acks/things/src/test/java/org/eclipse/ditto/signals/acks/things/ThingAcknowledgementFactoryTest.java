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

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Unit test for {@link ThingAcknowledgementFactory}.
 */
public final class ThingAcknowledgementFactoryTest {

    private static final AcknowledgementLabel KNOWN_LABEL = DittoAcknowledgementLabel.PERSISTED;
    private static final ThingId KNOWN_THING_ID = ThingId.generateRandom();
    private static final HttpStatusCode KNOWN_STATUS_CODE = HttpStatusCode.NOT_FOUND;
    private static final JsonObject KNOWN_PAYLOAD = JsonObject.newBuilder().set("foo", "bar").build();

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingAcknowledgementFactory.class, areImmutable());
    }

    @Test
    public void newAcknowledgementReturnsExpected() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, KNOWN_STATUS_CODE,
                        dittoHeaders, KNOWN_PAYLOAD);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat((CharSequence) acknowledgement.getLabel())
                    .as("same label")
                    .isEqualTo(KNOWN_LABEL);
            softly.assertThat((CharSequence) acknowledgement.getEntityId())
                    .as("same entity ID")
                    .isEqualTo(KNOWN_THING_ID);
            softly.assertThat(acknowledgement.getType())
                    .as("same type")
                    .isEqualTo(Acknowledgement.getType(KNOWN_THING_ID.getEntityType()));
            softly.assertThat(acknowledgement.getStatusCode())
                    .as("same status code")
                    .isEqualTo(KNOWN_STATUS_CODE);
            softly.assertThat(acknowledgement.getDittoHeaders())
                    .as("same DittoHeaders")
                    .isEqualTo(dittoHeaders);
            softly.assertThat(acknowledgement.getEntity())
                    .as("same entity")
                    .contains(KNOWN_PAYLOAD);
        }
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Acknowledgement acknowledgement =
                ThingAcknowledgementFactory.newAcknowledgement(KNOWN_LABEL, KNOWN_THING_ID, KNOWN_STATUS_CODE,
                        dittoHeaders, KNOWN_PAYLOAD);

        final Acknowledgement parsedAcknowledgement = ThingAcknowledgementFactory.fromJson(acknowledgement.toJson());

        assertThat(parsedAcknowledgement).isEqualTo(acknowledgement);
    }

}