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
package org.eclipse.ditto.signals.commands.thingsearch.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areEffectivelyImmutable;

import java.util.Arrays;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.query.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription}.
 */
public final class CreateSubscriptionTest {

    private static final String KNOWN_FIELDS = "thingId";

    private static final String JSON_ALL_FIELDS = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, CreateSubscription.TYPE)
            .set(CreateSubscription.JsonFields.FILTER, TestConstants.KNOWN_FILTER_STR)
            .set(CreateSubscription.JsonFields.OPTIONS, JsonFactory.newArrayBuilder()
                    .add(TestConstants.KNOWN_OPT_1)
                    .add(TestConstants.KNOWN_OPT_2)
                    .build())
            .set(CreateSubscription.JsonFields.FIELDS, KNOWN_FIELDS)
            .set(CreateSubscription.JsonFields.NAMESPACES, JsonFactory.newArrayBuilder()
                    .add(TestConstants.KNOWN_NAMESPACE)
                    .build())
            .build()
            .toString();

    private static final String JSON_MINIMAL = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, CreateSubscription.TYPE)
            .build().toString();

    @Test
    public void assertImmutability() {
        assertInstancesOf(CreateSubscription.class,
                areEffectivelyImmutable(),
                provided(JsonFieldSelector.class).isAlsoImmutable(),
                assumingFields("options").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CreateSubscription.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonWithAllFieldsSet() {
        final CreateSubscription command = CreateSubscription.of(TestConstants.KNOWN_FILTER_STR,
                Arrays.asList(TestConstants.KNOWN_OPT_1, TestConstants.KNOWN_OPT_2),
                JsonFactory.newFieldSelector(KNOWN_FIELDS, TestConstants.JSON_PARSE_OPTIONS),
                TestConstants.KNOWN_NAMESPACES_SET,
                DittoHeaders.empty());

        final String json = command.toJsonString();
        assertThat(json).isEqualTo(JSON_ALL_FIELDS);
    }

    @Test
    public void toJsonWithOnlyRequiredFieldsSet() {
        final CreateSubscription command = CreateSubscription.of(DittoHeaders.empty());
        final String json = command.toJsonString();
        assertThat(json).isEqualTo(JSON_MINIMAL);
    }

    @Test
    public void fromJsonWithAllFieldsSet() {
        assertAllFieldsSet(CreateSubscription.fromJson(JsonObject.of(JSON_ALL_FIELDS), DittoHeaders.empty()));
    }

    @Test
    public void fromJsonWithOnlyRequiredFieldsSet() {
        assertMinimal(CreateSubscription.fromJson(JsonObject.of(JSON_MINIMAL), DittoHeaders.empty()));
    }

    private static void assertAllFieldsSet(final CreateSubscription command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).contains(TestConstants.KNOWN_FILTER_STR);
        assertThat(command.getOptions()).contains(
                Arrays.asList(TestConstants.KNOWN_OPT_1, TestConstants.KNOWN_OPT_2));
        assertThat(command.getSelectedFields()).contains(
                JsonFactory.newFieldSelector(KNOWN_FIELDS, TestConstants.JSON_PARSE_OPTIONS));
    }

    private static void assertMinimal(final CreateSubscription command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).isEmpty();
        assertThat(command.getOptions()).isEmpty();
        assertThat(command.getSelectedFields()).isEmpty();
    }

}
