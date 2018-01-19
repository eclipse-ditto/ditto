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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyFeatureDefinitionResponse}.
 */
public final class ModifyFeatureDefinitionResponseTest {

    private static final JsonObject KNOWN_JSON_CREATED = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ModifyFeatureDefinitionResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.CREATED.toInt())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(ModifyFeatureDefinitionResponse.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(ModifyFeatureDefinitionResponse.JSON_DEFINITION,
                    TestConstants.Feature.FLUX_CAPACITOR_DEFINITION.toJson())
            .build();

    private static final JsonObject KNOWN_JSON_UPDATED = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ModifyFeatureDefinitionResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.NO_CONTENT.toInt())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(ModifyFeatureDefinitionResponse.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureDefinitionResponse.class,
                areImmutable(),
                provided(FeatureDefinition.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyFeatureDefinitionResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyFeatureDefinitionResponse underTestCreated =
                ModifyFeatureDefinitionResponse.created(TestConstants.Thing.THING_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_DEFINITION, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonCreated = underTestCreated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON_CREATED);

        final ModifyFeatureDefinitionResponse underTestUpdated = ModifyFeatureDefinitionResponse
                .modified(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonUpdated = underTestUpdated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonUpdated).isEqualTo(KNOWN_JSON_UPDATED);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyFeatureDefinitionResponse underTestCreated =
                ModifyFeatureDefinitionResponse.fromJson(KNOWN_JSON_CREATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestCreated).isNotNull();
        assertThat(underTestCreated.getFeatureDefinitionCreated())
                .hasValue(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION);

        final ModifyFeatureDefinitionResponse underTestUpdated =
                ModifyFeatureDefinitionResponse.fromJson(KNOWN_JSON_UPDATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestUpdated).isNotNull();
        assertThat(underTestUpdated.getFeatureDefinitionCreated()).isEmpty();
    }

}
