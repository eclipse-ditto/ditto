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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SudoCountThings}.
 */
public final class SudoCountThingsTest {

    private static final String KNOWN_FILTER_STR = "eq(thingId,4711)";

    private static final String JSON_ALL_FIELDS_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, SudoCountThings.TYPE)
            .set(SudoCountThings.JSON_FILTER, KNOWN_FILTER_STR)
            .build().toString();

    private static final String JSON_ALL_FIELDS_V1 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.ID, SudoCountThings.NAME)
            .set(SudoCountThings.JSON_FILTER, KNOWN_FILTER_STR)
            .build().toString();

    private static final String JSON_MINIMAL_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, SudoCountThings.TYPE)
            .build().toString();

    private static final String JSON_MINIMAL_V1 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.ID, SudoCountThings.NAME)
            .build().toString();

    /** */
    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SudoCountThings.class, MutabilityMatchers.areImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoCountThings.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    /** */
    @Test
    public void toJsonWithAllFieldsSetV2() {
        final SudoCountThings command = SudoCountThings.of(KNOWN_FILTER_STR, DittoHeaders.empty());

        final String json = command.toJsonString();
        assertThat(json).isEqualTo(JSON_ALL_FIELDS_V2);
    }

    /** */
    @Test
    public void toJsonWithAllFieldsSetV1() {
        final SudoCountThings command = SudoCountThings.of(KNOWN_FILTER_STR, DittoHeaders.empty());

        final String json = command.toJsonString(JsonSchemaVersion.V_1, FieldType.regularOrSpecial());
        assertThat(json).isEqualTo(JSON_ALL_FIELDS_V1);
    }

    /** */
    @Test
    public void toJsonWithOnlyRequiredFieldsSetV1() {
        final SudoCountThings command = SudoCountThings.of(DittoHeaders.empty());

        final String json = command.toJsonString(JsonSchemaVersion.V_1, FieldType.regularOrSpecial());

        assertThat(json).isEqualTo(JSON_MINIMAL_V1);
    }

    /** */
    @Test
    public void toJsonWithOnlyRequiredFieldsSetV2() {
        final SudoCountThings command = SudoCountThings.of(DittoHeaders.empty());

        final String json = command.toJsonString();

        assertThat(json).isEqualTo(JSON_MINIMAL_V2);
    }

    /** */
    @Test
    public void fromJsonWithAllFieldsSetV1() {
        assertAllFieldsSet(SudoCountThings.fromJson(JSON_ALL_FIELDS_V1, DittoHeaders.empty()));
    }

    /** */
    @Test
    public void fromJsonWithAllFieldsSetV2() {
        assertAllFieldsSet(SudoCountThings.fromJson(JSON_ALL_FIELDS_V2, DittoHeaders.empty()));
    }

    private static void assertAllFieldsSet(final SudoCountThings command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).contains(KNOWN_FILTER_STR);
    }

    /** */
    @Test
    public void fromJsonWithOnlyRequiredFieldsSetV1() {
        assertMinimal(SudoCountThings.fromJson(JSON_MINIMAL_V1, DittoHeaders.empty()));
    }

    /** */
    @Test
    public void fromJsonWithOnlyRequiredFieldsSetV2() {
        assertMinimal(SudoCountThings.fromJson(JSON_MINIMAL_V2, DittoHeaders.empty()));
    }

    private static void assertMinimal(final SudoCountThings command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).isEmpty();
    }

}
