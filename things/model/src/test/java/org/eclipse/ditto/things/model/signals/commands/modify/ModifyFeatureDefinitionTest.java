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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyFeatureDefinition}.
 */
public final class ModifyFeatureDefinitionTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyFeatureDefinition.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyFeatureDefinition.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(ModifyFeatureDefinition.JSON_DEFINITION, TestConstants.Feature.FLUX_CAPACITOR_DEFINITION.toJson())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureDefinition.class,
                areImmutable(),
                provided(FeatureDefinition.class, ThingId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyFeatureDefinition.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void tryToCreateInstanceWithNullThingId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ModifyFeatureDefinition.of(null, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_DEFINITION, TestConstants.EMPTY_DITTO_HEADERS))
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNullFeatureId() {
        assertThatNullPointerException()
                .isThrownBy(() -> ModifyFeatureDefinition.of(TestConstants.Thing.THING_ID, null,
                        TestConstants.Feature.FLUX_CAPACITOR_DEFINITION, TestConstants.EMPTY_DITTO_HEADERS))
                .withMessage("The %s must not be null!", "Feature ID")
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> ModifyFeatureDefinition.of(TestConstants.Thing.THING_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_ID, null, TestConstants.EMPTY_DITTO_HEADERS))
                .withMessage("The %s must not be null!", "Feature Definition")
                .withNoCause();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyFeatureDefinition underTest = ModifyFeatureDefinition.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.Feature.FLUX_CAPACITOR_DEFINITION,
                TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyFeatureDefinition underTest =
                ModifyFeatureDefinition.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(underTest.getDefinition()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION);
    }

}
