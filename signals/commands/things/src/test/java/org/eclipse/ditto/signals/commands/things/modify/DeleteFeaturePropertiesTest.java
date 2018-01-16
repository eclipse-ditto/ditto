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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DeleteFeatureProperties}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DeleteFeaturePropertiesTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, DeleteFeatureProperties.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(DeleteFeatureProperties.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeatureProperties.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DeleteFeatureProperties.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = ThingIdInvalidException.class)
    public void tryToCreateInstanceWithNullThingId() {
        DeleteFeatureProperties.of(null, TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        DeleteFeatureProperties.of(TestConstants.Thing.THING_ID, null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void createInstanceWithValidArguments() {
        final DeleteFeatureProperties underTest = DeleteFeatureProperties.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
    }


    @Test
    public void toJsonReturnsExpected() {
        final DeleteFeatureProperties underTest = DeleteFeatureProperties.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final DeleteFeatureProperties underTest =
                DeleteFeatureProperties.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
    }

}
