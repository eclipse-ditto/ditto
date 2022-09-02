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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests the {@link ThingConditionValidator}.
 */
public class ThingConditionValidatorTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingConditionValidator.class, areImmutable());
    }

    @Test
    public void validationSucceeds() {
        final var modifyThing = ModifyThing.of(
                TestConstants.Thing.THING_ID,
                TestConstants.Thing.THING_V2,
                null,
                DittoHeaders.empty());

        final var condition = "eq(attributes/maker,\"Bosch\")";
        final var validationError =
                ThingConditionValidator.validate(modifyThing, condition, TestConstants.Thing.THING_V2);
        softly.assertThat(validationError).isEmpty();
    }

    @Test
    public void validationSucceedsWithTimeValue() {
        final var modifyThing = ModifyThing.of(
                TestConstants.Thing.THING_ID,
                TestConstants.Thing.THING_V2,
                null,
                DittoHeaders.empty());

        final var condition = "eq(_modified,\"" + Instant.EPOCH + "\")";
        final var validationError =
                ThingConditionValidator.validate(modifyThing, condition, TestConstants.Thing.THING_V2);
        softly.assertThat(validationError).isEmpty();
    }

    @Test
    public void validationFails() {
        final var modifyThing = ModifyThing.of(
                TestConstants.Thing.THING_ID,
                TestConstants.Thing.THING_V2,
                null, DittoHeaders.empty());

        final var condition = "eq(attributes/maker,\"NotBosch\")";
        final var validationError =
                ThingConditionValidator.validate(modifyThing, condition, TestConstants.Thing.THING_V2);
        softly.assertThat(validationError).isPresent();
        softly.assertThat(validationError.get()).isInstanceOf(ThingConditionFailedException.class);
    }

    @Test
    public void noValidationErrorExpectedForCreateThing() {
        final var modifyThing = CreateThing.of(
                TestConstants.Thing.THING_V2,
                null,
                DittoHeaders.empty());

        final var condition = "eq(attributes/maker,\"NotBosch\")";
        final var validationError =
                ThingConditionValidator.validate(modifyThing, condition, TestConstants.Thing.THING_V2);
        softly.assertThat(validationError).isEmpty();
    }

    @Test
    public void noValidationErrorExpectedForNullEntity() {
        final var modifyThing = ModifyThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING_V2,
                null, DittoHeaders.empty());
        final var condition = "eq(attributes/maker,\"NotBosch\")";
        final var validationError =
                ThingConditionValidator.validate(modifyThing, condition, null);
        softly.assertThat(validationError).isEmpty();
    }

}
