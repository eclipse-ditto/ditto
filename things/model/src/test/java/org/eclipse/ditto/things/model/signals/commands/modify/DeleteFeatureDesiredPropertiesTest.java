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
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DeleteFeatureDesiredProperties}.
 */
public final class DeleteFeatureDesiredPropertiesTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, DeleteFeatureDesiredProperties.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(DeleteFeatureDesiredProperties.JSON_FEATURE_ID, TestConstants.Feature.HOVER_BOARD_ID)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeatureDesiredProperties.class, areImmutable(), provided(ThingId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DeleteFeatureDesiredProperties.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void tryToCreateInstanceWithNullThingIdString() {
        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> DeleteFeatureDesiredProperties.of(ThingId.of(null), TestConstants.Feature.HOVER_BOARD_ID,
                        TestConstants.EMPTY_DITTO_HEADERS));
    }

    @Test
    public void tryToCreateInstanceWithNullThingId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> DeleteFeatureDesiredProperties.of(null, TestConstants.Feature.HOVER_BOARD_ID,
                        TestConstants.EMPTY_DITTO_HEADERS))
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNullFeatureId() {
        assertThatNullPointerException()
                .isThrownBy(() -> DeleteFeatureDesiredProperties.of(TestConstants.Thing.THING_ID, null,
                        TestConstants.EMPTY_DITTO_HEADERS))
                .withMessage("The %s must not be null!", "featureId")
                .withNoCause();
    }

    @Test
    public void createInstanceWithValidArguments() {
        final DeleteFeatureDesiredProperties underTest = DeleteFeatureDesiredProperties.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
    }

    @Test
    public void toJsonReturnsExpected() {
        final DeleteFeatureDesiredProperties underTest = DeleteFeatureDesiredProperties.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final DeleteFeatureDesiredProperties underTest =
                DeleteFeatureDesiredProperties.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.HOVER_BOARD_ID);
    }

}
