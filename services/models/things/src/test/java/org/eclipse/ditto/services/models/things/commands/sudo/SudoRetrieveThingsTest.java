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
package org.eclipse.ditto.services.models.things.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.services.models.things.TestConstants.Thing;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoRetrieveThings}.
 */
public final class SudoRetrieveThingsTest {

    private static final JsonArray THING_IDS = JsonFactory.newArrayBuilder()
            .add(Thing.THING_ID, ":otherThingId")
            .build();

    private static final String SELECTED_FIELDS = "field1,field2,field3";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SudoRetrieveThings.TYPE)
            .set(SudoRetrieveThings.JSON_THING_IDS, THING_IDS)
            .build();

    private static final JsonObject KNOWN_JSON_WITH_FIELD_SELECTION = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SudoRetrieveThings.TYPE)
            .set(SudoRetrieveThings.JSON_THING_IDS, THING_IDS)
            .set(SudoCommand.JsonFields.SELECTED_FIELDS, SELECTED_FIELDS)
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    private static List<String> getThingIds() {
        return THING_IDS.stream().map(JsonValue::asString).collect(Collectors.toList());
    }

    private static JsonFieldSelector getJsonFieldSelector() {
        return JsonFactory.newFieldSelector(SELECTED_FIELDS,
                JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build());
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveThings.class,
                areImmutable(),
                provided(AuthorizationContext.class, JsonFieldSelector.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrieveThings.class)
                .withRedefinedSuperclass()
                .verify();
    }

    /** */
    @Test
    public void toJsonReturnsExpected() {
        final SudoRetrieveThings underTest = SudoRetrieveThings.of(getThingIds(), EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    /** */
    @Test
    public void createInstanceFromValidJson() {
        final SudoRetrieveThings underTest = SudoRetrieveThings.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingIds()).isEqualTo(getThingIds());
        assertThat(underTest.getSelectedFields()).isEqualTo(Optional.empty());
    }

    /** */
    @Test
    public void jsonSerializationWorksAsExpectedWithSelectedFields() {
        final SudoRetrieveThings underTest =
                SudoRetrieveThings.of(getThingIds(), getJsonFieldSelector(), EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_FIELD_SELECTION);
    }

    /** */
    @Test
    public void createInstanceFromValidJsonWithSelectedFields() {
        final SudoRetrieveThings underTest =
                SudoRetrieveThings.fromJson(KNOWN_JSON_WITH_FIELD_SELECTION.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingIds()).isEqualTo(getThingIds());
        assertThat(underTest.getSelectedFields()).isEqualTo(Optional.of(getJsonFieldSelector()));
    }

    /** */
    @Test
    public void checkSudoCommandTypeWorks() {
        final SudoRetrieveThings sudoRetrieveThings =
                SudoRetrieveThings.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        final SudoCommand sudoCommand = SudoCommandRegistry.newInstance()
                .parse(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(sudoRetrieveThings).isEqualTo(sudoCommand);
    }

}
