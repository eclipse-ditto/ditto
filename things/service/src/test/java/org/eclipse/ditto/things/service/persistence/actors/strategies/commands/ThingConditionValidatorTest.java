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

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.junit.Rule;
import org.junit.Test;

import scala.util.Either;

/**
 * Tests the {@link ThingConditionValidator}.
 */
public class ThingConditionValidatorTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private static final ThingConditionValidator underTest = ThingConditionValidator.getInstance();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingConditionValidator.class, areImmutable());
    }

    @Test
    public void validationSucceeds() {
        final ModifyThing modifyThing = ModifyThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING_V2,
                null, DittoHeaders.empty());
        final String condition = "eq(attributes/maker,\"Bosch\")";
        final Either<Void, ThingConditionFailedException> validate =
                underTest.validate(modifyThing, condition, TestConstants.Thing.THING_V2);
        softly.assertThat(validate.isLeft()).isTrue();
    }

    @Test
    public void validationFails() {
        final ModifyThing modifyThing = ModifyThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING_V2,
                null, DittoHeaders.empty());
        final String condition = "eq(attributes/maker,\"NotBosch\")";
        final Either<Void, ThingConditionFailedException> validate =
                underTest.validate(modifyThing, condition, TestConstants.Thing.THING_V2);
        softly.assertThat(validate.isRight()).isTrue();
        softly.assertThat(validate.right().get()).isInstanceOf(ThingConditionFailedException.class);
    }

}
