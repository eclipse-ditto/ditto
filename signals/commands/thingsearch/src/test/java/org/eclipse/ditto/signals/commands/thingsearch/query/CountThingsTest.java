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
package org.eclipse.ditto.signals.commands.thingsearch.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link CountThings}.
 */
public final class CountThingsTest {

    private static final String JSON_ALL_FIELDS_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, CountThings.TYPE)
            .set(CountThings.JSON_FILTER, TestConstants.Search.KNOWN_FILTER_STR)
            .set(CountThings.JSON_NAMESPACES, JsonFactory.newArrayBuilder()
                    .add(TestConstants.Search.KNOWN_NAMESPACE)
                    .build())
            .build().toString();

    private static final String JSON_ALL_FIELDS_V1 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.ID, CountThings.NAME)
            .set(CountThings.JSON_FILTER, TestConstants.Search.KNOWN_FILTER_STR)
            .set(CountThings.JSON_NAMESPACES, JsonFactory.newArrayBuilder()
                    .add(TestConstants.Search.KNOWN_NAMESPACE)
                    .build())
            .build().toString();

    private static final String JSON_MINIMAL_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, CountThings.TYPE)
            .build().toString();

    private static final String JSON_MINIMAL_V1 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.ID, CountThings.NAME)
            .build().toString();


    @Test
    public void assertImmutability() {
        assertInstancesOf(CountThings.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CountThings.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void toJsonWithAllFieldsSetV2() {
        final CountThings command = CountThings.of(TestConstants.Search.KNOWN_FILTER_STR,
                TestConstants.Search.KNOWN_NAMESPACES_SET, DittoHeaders.empty());

        final String json = command.toJsonString();
        assertThat(json).isEqualTo(JSON_ALL_FIELDS_V2);
    }


    @Test
    public void toJsonWithAllFieldsSetV1() {
        final CountThings command = CountThings.of(TestConstants.Search.KNOWN_FILTER_STR,
                TestConstants.Search.KNOWN_NAMESPACES_SET, DittoHeaders.empty());

        final String json = command.toJsonString(JsonSchemaVersion.V_1, FieldType.regularOrSpecial());
        assertThat(json).isEqualTo(JSON_ALL_FIELDS_V1);
    }


    @Test
    public void toJsonWithOnlyRequiredFieldsSetV1() {
        final CountThings command = CountThings.of(DittoHeaders.empty());

        final String json = command.toJsonString(JsonSchemaVersion.V_1, FieldType.regularOrSpecial());

        assertThat(json).isEqualTo(JSON_MINIMAL_V1);
    }


    @Test
    public void toJsonWithOnlyRequiredFieldsSetV2() {
        final CountThings command = CountThings.of(DittoHeaders.empty());

        final String json = command.toJsonString();

        assertThat(json).isEqualTo(JSON_MINIMAL_V2);
    }


    @Test
    public void fromJsonWithAllFieldsSetV1() {
        assertAllFieldsSet(CountThings.fromJson(JSON_ALL_FIELDS_V1, DittoHeaders.empty()));
    }


    @Test
    public void fromJsonWithAllFieldsSetV2() {
        assertAllFieldsSet(CountThings.fromJson(JSON_ALL_FIELDS_V2, DittoHeaders.empty()));
    }

    public void assertAllFieldsSet(final CountThings command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).contains(TestConstants.Search.KNOWN_FILTER_STR);
    }


    @Test
    public void fromJsonWithOnlyRequiredFieldsSetV1() {
        assertMinimal(CountThings.fromJson(JSON_MINIMAL_V1, DittoHeaders.empty()));
    }


    @Test
    public void fromJsonWithOnlyRequiredFieldsSetV2() {
        assertMinimal(CountThings.fromJson(JSON_MINIMAL_V2, DittoHeaders.empty()));
    }

    public void assertMinimal(final CountThings command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).isEmpty();
    }
}
