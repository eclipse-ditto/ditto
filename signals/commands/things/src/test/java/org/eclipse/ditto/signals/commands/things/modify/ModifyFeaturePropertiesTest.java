/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.things.modify;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.model.things.ThingTooLargeException;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyFeatureProperties}.
 */
public final class ModifyFeaturePropertiesTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyFeatureProperties.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(ModifyFeatureProperties.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(ModifyFeatureProperties.JSON_PROPERTIES, TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureProperties.class,
                areImmutable(),
                provided(FeatureProperties.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyFeatureProperties.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = ThingIdInvalidException.class)
    public void tryToCreateInstanceWithNullThingId() {
        ModifyFeatureProperties.of(null, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        ModifyFeatureProperties.of(TestConstants.Thing.THING_ID, null, TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullProperties() {
        ModifyFeatureProperties.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyFeatureProperties underTest = ModifyFeatureProperties.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES,
                TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyFeatureProperties underTest =
                ModifyFeatureProperties.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        Assertions.assertThat(underTest.getProperties()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);
    }

    @Test
    public void modifyTooLargeFeatureProperties() {
        final StringBuilder sb = new StringBuilder();
        for(int i=0; i<TestConstants.THING_SIZE_LIMIT_BYTES; i++) {
            sb.append('a');
        }
        sb.append('b');

        final FeatureProperties featureProperties = FeatureProperties.newBuilder().set("a", JsonValue.of(sb.toString())).build();

        assertThatThrownBy(() -> ModifyFeatureProperties.of("foo:bar", "foo", featureProperties,
                DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
