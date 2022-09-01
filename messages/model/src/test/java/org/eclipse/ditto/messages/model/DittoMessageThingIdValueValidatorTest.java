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
package org.eclipse.ditto.messages.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.junit.Test;

public final class DittoMessageThingIdValueValidatorTest {

    private static final DittoMessageThingIdValueValidator underTest = DittoMessageThingIdValueValidator.getInstance();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoMessageThingIdValueValidator.class, areImmutable());
    }

    @Test
    public void throwsHeaderInvalidExceptionIfInvalidThingId() {
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(MessageHeaderDefinition.DIRECTION, "%"))
                .withCauseInstanceOf(ThingIdInvalidException.class);
    }

    @Test
    public void successfullyValidatesValidThingIdInDefaultNamespace() {
        assertThatCode(() -> underTest.accept(MessageHeaderDefinition.DIRECTION, ":test"))
                .doesNotThrowAnyException();
    }

    @Test
    public void successfullyValidatesValidThingIdInDefinedNamespace() {
        assertThatCode(() -> underTest.accept(MessageHeaderDefinition.DIRECTION, "foo:bar"))
                .doesNotThrowAnyException();
    }

}
