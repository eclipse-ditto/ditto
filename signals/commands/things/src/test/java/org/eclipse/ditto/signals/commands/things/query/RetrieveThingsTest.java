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

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveThings}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class RetrieveThingsTest {

    private static final JsonArray THING_IDS = JsonFactory.newArrayBuilder()
            .add(TestConstants.Thing.THING_ID)
            .add(":AnotherThingId")
            .build();

    private static final String SELECTED_FIELDS = "field1,field2,field3";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveThings.TYPE)
            .set(RetrieveThings.JSON_THING_IDS, THING_IDS)
            .build();

    private static final JsonObject KNOWN_JSON_WITH_FIELD_SELECTION = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveThings.TYPE)
            .set(RetrieveThings.JSON_THING_IDS, THING_IDS)
            .set(RetrieveThings.JSON_SELECTED_FIELDS, SELECTED_FIELDS)
            .build();

    private static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    private static List<String> getThingIds() {
        return THING_IDS.stream().map(JsonValue::asString).collect(Collectors.toList());
    }

    private static JsonFieldSelector getJsonFieldSelector() {
        return JsonFactory.newFieldSelector(SELECTED_FIELDS, JSON_PARSE_OPTIONS);
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveThings.class,
                areImmutable(),
                provided(AuthorizationContext.class, JsonFieldSelector.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveThings.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrieveThings underTest = RetrieveThings.getBuilder(getThingIds()).build();
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrieveThings underTest =
                RetrieveThings.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingIds()).isEqualTo(getThingIds());
        assertThat(underTest.getSelectedFields()).isEmpty();
    }


    @Test
    public void jsonSerializationWorksAsExpectedWithSelectedFields() {
        final RetrieveThings underTest =
                RetrieveThings.getBuilder(getThingIds()).selectedFields(getJsonFieldSelector()).build();
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_FIELD_SELECTION);
    }


    @Test
    public void createInstanceFromValidJsonWithSelectedFields() {
        final RetrieveThings underTest =
                RetrieveThings.fromJson(KNOWN_JSON_WITH_FIELD_SELECTION.toString(),
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingIds()).isEqualTo(getThingIds());
        assertThat(underTest.getSelectedFields()).contains(getJsonFieldSelector());
    }


    @Test
    public void checkRetrieveThingsWithEmptyJsonFieldSelectorBehavesEquallyAsOmittingFields() {
        final JsonFieldSelector selectedFields = JsonFactory.newFieldSelector(null, JSON_PARSE_OPTIONS);
        final RetrieveThings retrieveThings = RetrieveThings.getBuilder(TestConstants.Thing.THING_ID, ":AnotherThingId")
                .selectedFields(selectedFields)
                .build();

        final RetrieveThings retrieveThings2 =
                RetrieveThings.getBuilder(TestConstants.Thing.THING_ID, ":AnotherThingId")
                        .build();

        assertThat(retrieveThings).isEqualTo(retrieveThings2);
    }

}
