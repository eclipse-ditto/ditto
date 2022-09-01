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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyFeatureDesiredProperties}.
 */
public final class ModifyFeatureDesiredPropertiesTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyFeatureDesiredProperties.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyFeatureDesiredProperties.JSON_FEATURE_ID, TestConstants.Feature.HOVER_BOARD_ID)
            .set(ModifyFeatureDesiredProperties.JSON_DESIRED_PROPERTIES,
                    TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTIES)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureDesiredProperties.class,
                areImmutable(),
                provided(FeatureProperties.class, ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyFeatureDesiredProperties.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToCreateInstanceWithNullThingIdString() {
        ModifyFeatureDesiredProperties.of(ThingId.of(null), TestConstants.Feature.HOVER_BOARD_ID,
                TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTIES, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        ModifyFeatureDesiredProperties.of(null, TestConstants.Feature.HOVER_BOARD_ID,
                TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTIES, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        ModifyFeatureDesiredProperties.of(TestConstants.Thing.THING_ID, null,
                TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTIES,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullProperties() {
        ModifyFeatureDesiredProperties.of(TestConstants.Thing.THING_ID, TestConstants.Feature.HOVER_BOARD_ID,
                null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyFeatureDesiredProperties underTest = ModifyFeatureDesiredProperties.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID, TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTIES,
                TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyFeatureDesiredProperties underTest =
                ModifyFeatureDesiredProperties.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.HOVER_BOARD_ID);
        Assertions.assertThat(underTest.getDesiredProperties())
                .isEqualTo(TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTIES);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceFromJsonWithInvalidPropertiesPath() {

        final FeatureProperties featurePropertiesWithInvalidPath =
                TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTIES
                        .setValue("valid", JsonFactory.newObjectBuilder().set("invälid", JsonValue.of(42)).build());

        final JsonObject invalidJson = KNOWN_JSON.toBuilder()
                .set(ModifyFeatureDesiredProperties.JSON_DESIRED_PROPERTIES, featurePropertiesWithInvalidPath)
                .build();

        ModifyFeatureDesiredProperties.fromJson(invalidJson.toString(), TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void modifyTooLargeFeatureProperties() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TestConstants.THING_SIZE_LIMIT_BYTES; i++) {
            sb.append('a');
        }
        sb.append('b');

        final FeatureProperties featureDesiredProperties =
                FeatureProperties.newBuilder().set("a", JsonValue.of(sb.toString())).build();

        assertThatThrownBy(() ->
                ModifyFeatureDesiredProperties.of(ThingId.of("foo", "bar"),
                        "foo", featureDesiredProperties,
                        DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
