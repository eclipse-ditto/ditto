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
package org.eclipse.ditto.thingsearch.model.signals.commands.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areEffectivelyImmutable;

import java.util.Arrays;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link QueryThings}.
 */
public final class QueryThingsTest {

    private static final String KNOWN_FIELDS = "thingId";

    private static final String JSON_ALL_FIELDS_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, QueryThings.TYPE)
            .set(QueryThings.JSON_FILTER, TestConstants.KNOWN_FILTER_STR)
            .set(QueryThings.JSON_OPTIONS, JsonFactory.newArrayBuilder()
                    .add(TestConstants.KNOWN_OPT_1)
                    .add(TestConstants.KNOWN_OPT_2)
                    .build())
            .set(QueryThings.JSON_FIELDS, KNOWN_FIELDS)
            .set(QueryThings.JSON_NAMESPACES, JsonFactory.newArrayBuilder()
                    .add(TestConstants.KNOWN_NAMESPACE)
                    .build())
            .build()
            .toString();

    private static final String JSON_MINIMAL_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, QueryThings.TYPE)
            .build().toString();

    @Test
    public void assertImmutability() {
        assertInstancesOf(QueryThings.class,
                areEffectivelyImmutable(),
                provided(JsonFieldSelector.class).isAlsoImmutable(),
                assumingFields("options").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(QueryThings.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonWithAllFieldsSetV2() {
        final QueryThings command = QueryThings.of(TestConstants.KNOWN_FILTER_STR,
                Arrays.asList(TestConstants.KNOWN_OPT_1, TestConstants.KNOWN_OPT_2),
                JsonFactory.newFieldSelector(KNOWN_FIELDS, TestConstants.JSON_PARSE_OPTIONS),
                TestConstants.KNOWN_NAMESPACES_SET,
                DittoHeaders.empty());

        final String json = command.toJsonString();
        assertThat(json).isEqualTo(JSON_ALL_FIELDS_V2);
    }

    @Test
    public void toJsonWithOnlyRequiredFieldsSetV2() {
        final QueryThings command = QueryThings.of(DittoHeaders.empty());

        final String json = command.toJsonString();

        assertThat(json).isEqualTo(JSON_MINIMAL_V2);
    }

    @Test
    public void fromJsonWithAllFieldsSetV2() {
        assertAllFieldsSet(QueryThings.fromJson(JSON_ALL_FIELDS_V2, DittoHeaders.empty()));
    }

    private static void assertAllFieldsSet(final QueryThings command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).contains(TestConstants.KNOWN_FILTER_STR);
        assertThat(command.getOptions()).contains(
                Arrays.asList(TestConstants.KNOWN_OPT_1, TestConstants.KNOWN_OPT_2));
        assertThat(command.getFields()).contains(
                JsonFactory.newFieldSelector(KNOWN_FIELDS, TestConstants.JSON_PARSE_OPTIONS));
    }

    @Test
    public void fromJsonWithOnlyRequiredFieldsSetV2() {
        assertMinimal(QueryThings.fromJson(JSON_MINIMAL_V2, DittoHeaders.empty()));
    }

    private static void assertMinimal(final QueryThings command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).isEmpty();
        assertThat(command.getOptions()).isEmpty();
        assertThat(command.getFields()).isEmpty();
    }

}
