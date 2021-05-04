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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveThing}.
 */
public final class RetrieveThingTest {

    private static final String SELECTED_FIELDS = "field1,field2,field3";
    private static final long SNAPSHOT_REVISION = 23L;

    private static final JsonParseOptions JSON_PARSE_OPTIONS = JsonFactory.newParseOptionsBuilder()
            .withoutUrlDecoding()
            .build();

    private static final JsonFieldSelector JSON_FIELD_SELECTOR = JsonFactory.newFieldSelector(SELECTED_FIELDS,
            JSON_PARSE_OPTIONS);

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveThing.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .build();

    private static final JsonObject KNOWN_JSON_WITH_FIELD_SELECTION = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveThing.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(RetrieveThing.JSON_SELECTED_FIELDS, SELECTED_FIELDS)
            .build();

    private static final JsonObject KNOWN_JSON_WITH_SNAPSHOT_REVISION = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveThing.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(RetrieveThing.JSON_SNAPSHOT_REVISION, SNAPSHOT_REVISION)
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveThing.class,
                areImmutable(),
                provided(JsonFieldSelector.class, ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveThing.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetBuilderWithNullThingId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveThing.getBuilder(null, DittoHeaders.empty()))
                .withNoCause();
    }


    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetBuilderWithNullDittoHeaders() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveThing.getBuilder(TestConstants.Thing.THING_ID, null))
                .withNoCause();
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrieveThing underTest = RetrieveThing.of(TestConstants.Thing.THING_ID, DittoHeaders.empty());
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrieveThing underTest =
                RetrieveThing.fromJson(KNOWN_JSON.toString(), DittoHeaders.empty());

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getSelectedFields()).isEmpty();
    }


    @Test
    public void jsonSerializationWorksAsExpectedWithSelectedFields() {
        final RetrieveThing underTest = RetrieveThing.getBuilder(TestConstants.Thing.THING_ID, DittoHeaders.empty())
                .withSelectedFields(JSON_FIELD_SELECTOR)
                .build();
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_FIELD_SELECTION);
    }


    @Test
    public void createInstanceFromValidJsonWithSelectedFields() {
        final RetrieveThing underTest = RetrieveThing.fromJson(KNOWN_JSON_WITH_FIELD_SELECTION.toString(),
                DittoHeaders.empty());

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getSelectedFields()).contains(JSON_FIELD_SELECTOR);
        assertThat(underTest.getSnapshotRevision()).isEmpty();
    }


    @Test
    public void getSelectedFieldsReturnsEmptyOptionalIfUnspecified() {
        final RetrieveThing underTest = RetrieveThing.of(TestConstants.Thing.THING_ID, DittoHeaders.empty());

        assertThat(underTest.getSelectedFields()).isEmpty();
    }


    @Test
    public void getSelectedFieldsReturnsExpected() {
        final RetrieveThing underTest = RetrieveThing.getBuilder(TestConstants.Thing.THING_ID, DittoHeaders.empty())
                .withSelectedFields(JSON_FIELD_SELECTOR)
                .build();

        assertThat(underTest.getSelectedFields()).contains(JSON_FIELD_SELECTOR);
    }


    @Test
    public void getSnapshotRevisionReturnsEmptyOptionalIfRevisionIsUnspecified() {
        final RetrieveThing underTest = RetrieveThing.of(TestConstants.Thing.THING_ID, DittoHeaders.empty());

        assertThat(underTest.getSnapshotRevision()).isEmpty();
    }


    @Test
    public void getSnapshotRevisionReturnsExpected() {
        final RetrieveThing underTest = RetrieveThing.getBuilder(TestConstants.Thing.THING_ID, DittoHeaders.empty())
                .withSnapshotRevision(SNAPSHOT_REVISION)
                .build();

        assertThat(underTest.getSnapshotRevision()).contains(SNAPSHOT_REVISION);
    }


    @Test
    public void jsonSerializationWithSnapshotRevisionWorksAsExpected() {
        final RetrieveThing underTest = RetrieveThing.getBuilder(TestConstants.Thing.THING_ID, DittoHeaders.empty())
                .withSnapshotRevision(SNAPSHOT_REVISION)
                .build();
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_SNAPSHOT_REVISION);
    }


    @Test
    public void createInstanceFromValidJsonWithSnapshotRevision() {
        final RetrieveThing underTest = RetrieveThing.fromJson(KNOWN_JSON_WITH_SNAPSHOT_REVISION.toString(),
                DittoHeaders.empty());

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getSelectedFields()).isEmpty();
        assertThat(underTest.getSnapshotRevision()).contains(SNAPSHOT_REVISION);
    }

}
