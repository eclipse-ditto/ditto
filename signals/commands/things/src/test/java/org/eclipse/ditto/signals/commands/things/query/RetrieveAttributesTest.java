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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveAttributes}.
 */
public final class RetrieveAttributesTest {

    private static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    private static final JsonFieldSelector KNOWN_JSON_LOCATOR =
            JsonFactory.newFieldSelector("key1(foo,bar)", JSON_PARSE_OPTIONS);

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveAttributes.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(RetrieveAttributes.JSON_SELECTED_FIELDS, KNOWN_JSON_LOCATOR.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAttributes.class, areImmutable(),
                provided(JsonFieldSelector.class, ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveAttributes.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToCreateInstanceWithNullThingIdString() {
        RetrieveAttributes.of((String) null, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        RetrieveAttributes.of((ThingId) null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void tryToCreateInstanceWithValidArguments() {
        RetrieveAttributes.of(TestConstants.Thing.THING_ID, KNOWN_JSON_LOCATOR, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrieveAttributes underTest =
                RetrieveAttributes.of(TestConstants.Thing.THING_ID, KNOWN_JSON_LOCATOR,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrieveAttributes underTest =
                RetrieveAttributes.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getSelectedFields()).contains(KNOWN_JSON_LOCATOR);
    }

}
