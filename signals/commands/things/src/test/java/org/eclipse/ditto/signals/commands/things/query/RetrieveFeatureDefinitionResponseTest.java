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
package org.eclipse.ditto.signals.commands.things.query;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonArray;
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
 * Unit test for {@link RetrieveFeatureDefinitionResponse}.
 */
public final class RetrieveFeatureDefinitionResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, RetrieveFeatureDefinitionResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(RetrieveFeatureDefinitionResponse.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(RetrieveFeatureDefinitionResponse.JSON_DEFINITION,
                    TestConstants.Feature.FLUX_CAPACITOR_DEFINITION.toJson())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureDefinitionResponse.class, areImmutable(),
                provided(JsonArray.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeatureDefinitionResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void tryToCreateInstanceWithNullFeatureDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> RetrieveFeatureDefinitionResponse.of(TestConstants.Thing.THING_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_ID, (FeatureDefinition) null,
                        TestConstants.EMPTY_DITTO_HEADERS))
                .withMessage("The %s must not be null!", "Definition")
                .withNoCause();
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrieveFeatureDefinitionResponse underTest =
                RetrieveFeatureDefinitionResponse.of(TestConstants.Thing.THING_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_DEFINITION, TestConstants.EMPTY_DITTO_HEADERS);
        final String actualJsonString = underTest.toJson(FieldType.regularOrSpecial()).toString();

        assertThat(actualJsonString).isEqualTo(KNOWN_JSON.toString());
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrieveFeatureDefinitionResponse underTest =
                RetrieveFeatureDefinitionResponse.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getDefinition()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION);
    }

}
