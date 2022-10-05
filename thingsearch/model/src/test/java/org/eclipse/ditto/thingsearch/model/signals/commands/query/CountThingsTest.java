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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link CountThings}.
 */
public final class CountThingsTest {

    private static final String JSON_ALL_FIELDS_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, CountThings.TYPE)
            .set(CountThings.JSON_FILTER, TestConstants.KNOWN_FILTER_STR)
            .set(CountThings.JSON_NAMESPACES, JsonFactory.newArrayBuilder()
                    .add(TestConstants.KNOWN_NAMESPACE)
                    .build())
            .build().toString();

    private static final String JSON_MINIMAL_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, CountThings.TYPE)
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
        final CountThings command = CountThings.of(TestConstants.KNOWN_FILTER_STR,
                TestConstants.KNOWN_NAMESPACES_SET, DittoHeaders.empty());

        final String json = command.toJsonString();
        assertThat(json).isEqualTo(JSON_ALL_FIELDS_V2);
    }

    @Test
    public void toJsonWithOnlyRequiredFieldsSetV2() {
        final CountThings command = CountThings.of(DittoHeaders.empty());

        final String json = command.toJsonString();

        assertThat(json).isEqualTo(JSON_MINIMAL_V2);
    }

    @Test
    public void fromJsonWithAllFieldsSetV2() {
        assertAllFieldsSet(CountThings.fromJson(JSON_ALL_FIELDS_V2, DittoHeaders.empty()));
    }

    public void assertAllFieldsSet(final CountThings command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).contains(TestConstants.KNOWN_FILTER_STR);
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
