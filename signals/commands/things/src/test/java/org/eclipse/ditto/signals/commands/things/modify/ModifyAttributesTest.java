/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.AttributesModelFactory;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyAttributes}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ModifyAttributesTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyAttributes.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(ModifyAttributes.JSON_ATTRIBUTES, TestConstants.Thing.ATTRIBUTES)
            .build();

    private static final JsonObject JSON_WITH_NULL_ATTRIBUTE = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyAttributes.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(ModifyAttributes.JSON_ATTRIBUTES, JsonFactory.nullObject())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyAttributes.class, areImmutable(), provided(Attributes.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyAttributes.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullAttribute() {
        ModifyAttributes.of(TestConstants.Thing.THING_ID, null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyAttributes underTest = ModifyAttributes.of(TestConstants.Thing.THING_ID,
                TestConstants.Thing.ATTRIBUTES, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyAttributes underTest =
                ModifyAttributes.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Thing.THING_ID);
        Assertions.assertThat(underTest.getAttributes()).isEqualTo(TestConstants.Thing.ATTRIBUTES);
    }

    @Test
    public void createInstanceFromValidJsonWithNullAttribute() {
        final ModifyAttributes underTest =
                ModifyAttributes.fromJson(JSON_WITH_NULL_ATTRIBUTE.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Thing.THING_ID);
        Assertions.assertThat(underTest.getAttributes()).isEqualTo(AttributesModelFactory.nullAttributes());
    }

}
