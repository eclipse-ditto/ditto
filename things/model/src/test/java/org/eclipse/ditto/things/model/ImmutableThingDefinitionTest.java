/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ImmutableThingDefinitionTest {

    private static final String NAMESPACE = "namespace";
    private static final String NAME = "name";
    private static final String VERSION = "1.0.0";

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableThingDefinition.class,
                areImmutable(),
                provided(DefinitionIdentifier.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableThingDefinition.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ImmutableThingDefinition thingDefinition = ImmutableThingDefinition.getInstance(NAMESPACE, NAME, VERSION);
        final JsonValue jsonValue = thingDefinition.toJson();
        assertThat(jsonValue).isEqualTo(JsonValue.of(String.format("%s:%s:%s", NAMESPACE, NAME, VERSION)));
    }
}
