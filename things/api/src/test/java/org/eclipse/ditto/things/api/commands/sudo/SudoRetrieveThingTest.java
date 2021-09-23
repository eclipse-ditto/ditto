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

import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoRetrieveThing}.
 */
public final class SudoRetrieveThingTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test", "myThing");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(SudoCommand.JsonFields.TYPE, SudoRetrieveThing.TYPE)
            .set(SudoCommand.JsonFields.JSON_THING_ID, THING_ID.toString())
            .set(SudoRetrieveThing.JSON_USE_ORIGINAL_SCHEMA_VERSION, false)
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveThing.class,
                areImmutable(),
                provided(JsonFieldSelector.class, ThingId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrieveThing.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final SudoRetrieveThing underTest = SudoRetrieveThing.of(THING_ID, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SudoRetrieveThing underTest = SudoRetrieveThing.fromJson(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(THING_ID);
        assertThat(underTest.getSelectedFields()).isEqualTo(Optional.empty());
        assertThat(underTest.useOriginalSchemaVersion()).isFalse();
    }

    @Test
    public void checkSudoCommandRegistryWorks() {
        final SudoRetrieveThing sudoRetrieveThing =
                SudoRetrieveThing.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        final Command sudoCommand = GlobalCommandRegistry.getInstance().parse(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        assertThat(sudoRetrieveThing).isEqualTo(sudoCommand);
    }

    @Test
    public void toJsonWithUsingOriginalSchemaVersionReturnsExpected() {
        final SudoRetrieveThing sudoRetrieveThing =
                SudoRetrieveThing.withOriginalSchemaVersion(THING_ID, null, EMPTY_DITTO_HEADERS);

        final JsonObject jsonObject = sudoRetrieveThing.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial());

        assertThat(jsonObject).contains(SudoRetrieveThing.JSON_USE_ORIGINAL_SCHEMA_VERSION, JsonFactory.newValue(true));
    }

    @Test
    public void fromJsonWithUseOriginalSchemaVersionTrueReturnsExpected() {
        final JsonObject jsonObject = KNOWN_JSON.toBuilder()
                .set(SudoRetrieveThing.JSON_USE_ORIGINAL_SCHEMA_VERSION, true)
                .build();

        final SudoRetrieveThing underTest = SudoRetrieveThing.fromJson(jsonObject, EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(THING_ID);
        assertThat(underTest.getSelectedFields()).isEqualTo(Optional.empty());
        assertThat(underTest.useOriginalSchemaVersion()).isTrue();
    }

}
