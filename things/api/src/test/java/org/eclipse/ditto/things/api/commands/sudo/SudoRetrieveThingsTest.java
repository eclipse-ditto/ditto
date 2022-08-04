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
package org.eclipse.ditto.things.api.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandRegistry;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.api.TestConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoRetrieveThings}.
 */
public final class SudoRetrieveThingsTest {

    private static final JsonArray THING_IDS = JsonFactory.newArrayBuilder()
            .add(TestConstants.Thing.THING_ID.toString(), ThingId.of(TestConstants.Thing.THING_ID.getNamespace(), "otherThingId").toString())
            .build();

    private static final String SELECTED_FIELDS = "field1,field2,field3";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SudoRetrieveThings.TYPE)
            .set(SudoRetrieveThings.JSON_THING_IDS, THING_IDS)
            .build();

    private static final JsonObject KNOWN_JSON_WITH_FIELD_SELECTION = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SudoRetrieveThings.TYPE)
            .set(SudoRetrieveThings.JSON_THING_IDS, THING_IDS)
            .set(ThingSudoCommand.JsonFields.SELECTED_FIELDS, SELECTED_FIELDS)
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    private static List<ThingId> getThingIds() {
        return THING_IDS.stream().map(JsonValue::asString).map(ThingId::of).collect(Collectors.toList());
    }

    private static JsonFieldSelector getJsonFieldSelector() {
        return JsonFactory.newFieldSelector(SELECTED_FIELDS,
                JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build());
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveThings.class,
                areImmutable(),
                provided(AuthorizationContext.class, JsonFieldSelector.class, ThingId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrieveThings.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final SudoRetrieveThings underTest = SudoRetrieveThings.of(getThingIds(), EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SudoRetrieveThings underTest = SudoRetrieveThings.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingIds()).isEqualTo(getThingIds());
        assertThat(underTest.getSelectedFields()).isEqualTo(Optional.empty());
    }

    @Test
    public void jsonSerializationWorksAsExpectedWithSelectedFields() {
        final SudoRetrieveThings underTest =
                SudoRetrieveThings.of(getThingIds(), getJsonFieldSelector(), EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_FIELD_SELECTION);
    }

    @Test
    public void createInstanceFromValidJsonWithSelectedFields() {
        final SudoRetrieveThings underTest =
                SudoRetrieveThings.fromJson(KNOWN_JSON_WITH_FIELD_SELECTION.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingIds()).isEqualTo(getThingIds());
        assertThat(underTest.getSelectedFields()).isEqualTo(Optional.of(getJsonFieldSelector()));
    }

    @Test
    public void checkSudoCommandTypeWorks() {
        final SudoRetrieveThings sudoRetrieveThings =
                SudoRetrieveThings.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        final Command sudoCommand = GlobalCommandRegistry.getInstance().parse(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        assertThat(sudoRetrieveThings).isEqualTo(sudoCommand);
    }

}
