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
package org.eclipse.ditto.things.model.signals.commands.query;

import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandRegistry;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.MissingThingIdsException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveThings}.
 */
public final class RetrieveThingsTest {

    private static final JsonArray THING_IDS = JsonFactory.newArrayBuilder()
            .add(TestConstants.Thing.THING_ID.toString())
            .add(TestConstants.Thing.THING_ID.getNamespace() + ":AnotherThingId")
            .build();

    private static final JsonArray THING_IDS_WITH_DISTINCT_NAMESPACE = JsonFactory.newArrayBuilder()
            .add(TestConstants.Thing.THING_ID.toString())
            .add(TestConstants.Thing.THING_ID + "1")
            .add(TestConstants.Thing.THING_ID + "2")
            .build();

    private static final String SELECTED_FIELDS = "field1,field2,field3";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveThings.TYPE)
            .set(RetrieveThings.JSON_THING_IDS, THING_IDS)
            .build();

    private static final JsonObject KNOWN_JSON_WITH_DISTINCT_NAMESPACE = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveThings.TYPE)
            .set(RetrieveThings.JSON_THING_IDS, THING_IDS_WITH_DISTINCT_NAMESPACE)
            .set(RetrieveThings.JSON_NAMESPACE, "example.com")
            .build();

    private static final JsonObject KNOWN_JSON_WITH_FIELD_SELECTION = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveThings.TYPE)
            .set(RetrieveThings.JSON_THING_IDS, THING_IDS)
            .set(RetrieveThings.JSON_SELECTED_FIELDS, SELECTED_FIELDS)
            .build();

    private static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    private static List<ThingId> getThingIds() {
        return THING_IDS.stream()
                .map(JsonValue::asString)
                .map(ThingId::of)
                .collect(Collectors.toList());
    }

    private static List<ThingId> getThingIdsWithDistinctNamespace() {
        return THING_IDS_WITH_DISTINCT_NAMESPACE.stream()
                .map(JsonValue::asString)
                .map(ThingId::of)
                .collect(Collectors.toList());
    }

    private static JsonFieldSelector getJsonFieldSelector() {
        return JsonFactory.newFieldSelector(SELECTED_FIELDS, JSON_PARSE_OPTIONS);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveThings.class,
                areImmutable(),
                provided(AuthorizationContext.class, JsonFieldSelector.class, ThingId.class).isAlsoImmutable());
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

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void toJsonReturnsExpectedWithNamespace() {
        final RetrieveThings underTest =
                RetrieveThings.getBuilder(getThingIdsWithDistinctNamespace()).namespace("example.com").build();
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_DISTINCT_NAMESPACE);
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrieveThings underTest =
                RetrieveThings.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getEntityIds()).isEqualTo(getThingIds());
        assertThat(underTest.getSelectedFields()).isEmpty();
        assertThat(underTest.getNamespace()).isEmpty();
    }

    @Test
    public void createInstanceWithNamespaceFromValidJson() {
        final RetrieveThings underTest =
                RetrieveThings.fromJson(KNOWN_JSON_WITH_DISTINCT_NAMESPACE.toString(),
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getEntityIds()).isEqualTo(
                THING_IDS_WITH_DISTINCT_NAMESPACE.stream()
                        .map(JsonValue::asString)
                        .map(ThingId::of)
                        .collect(Collectors.toList()));
        assertThat(underTest.getSelectedFields()).isEmpty();
        assertThat(underTest.getNamespace()).contains("example.com");
    }

    @Test
    public void jsonSerializationWorksAsExpectedWithSelectedFields() {
        final RetrieveThings underTest =
                RetrieveThings.getBuilder(getThingIds()).selectedFields(getJsonFieldSelector()).build();
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_FIELD_SELECTION);
    }

    @Test
    public void createInstanceFromValidJsonWithSelectedFields() {
        final RetrieveThings underTest =
                RetrieveThings.fromJson(KNOWN_JSON_WITH_FIELD_SELECTION.toString(),
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getEntityIds()).isEqualTo(getThingIds());
        assertThat(underTest.getSelectedFields()).contains(getJsonFieldSelector());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createInstanceWithInvalidNamespacesThrowsException() {
        RetrieveThings.getBuilder(getThingIds()).namespace("").build();
    }

    @Test
    public void checkRetrieveThingsWithEmptyJsonFieldSelectorBehavesEquallyAsOmittingFields() {
        final JsonFieldSelector selectedFields = JsonFactory.newFieldSelector(null, JSON_PARSE_OPTIONS);
        final RetrieveThings retrieveThings = RetrieveThings
                .getBuilder(TestConstants.Thing.THING_ID, ThingId.inDefaultNamespace("AnotherThingId"))
                .selectedFields(selectedFields)
                .build();

        final RetrieveThings retrieveThings2 =
                RetrieveThings.getBuilder(TestConstants.Thing.THING_ID, ThingId.inDefaultNamespace("AnotherThingId"))
                        .build();

        assertThat(retrieveThings).isEqualTo(retrieveThings2);
    }

    @Test(expected = NullPointerException.class)
    public void initializationWithNullForThingIdsArrayThrowsNullPointerException(){
        RetrieveThings.getBuilder((ThingId[]) null).build();
    }

    @Test(expected = NullPointerException.class)
    public void initializationWithNullForThingIdsListThrowsNullPointerException(){
        RetrieveThings.getBuilder((List<ThingId>) null).build();
    }

    @Test(expected = MissingThingIdsException.class)
    public void initializationWithoutThingIdsThrowsMissingThingIdsException(){
        RetrieveThings.getBuilder(Collections.emptyList()).build();
    }

    @Test(expected = MissingThingIdsException.class)
    public void initializationWithEmptyThingIdsListThrowsMissingThingIdsException(){
        RetrieveThings.getBuilder(new ArrayList<>()).build();
    }

    @Test
    public void parseRetrieveThingCommand() {
        final GlobalCommandRegistry commandRegistry = GlobalCommandRegistry.getInstance();

        final RetrieveThing command = RetrieveThing.of(
                TestConstants.Thing.THING_ID, TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = command.toJson(FieldType.regularOrSpecial());

        final Command<?> parsedCommand = commandRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        Assertions.assertThat(parsedCommand).isEqualTo(command);
    }
}
