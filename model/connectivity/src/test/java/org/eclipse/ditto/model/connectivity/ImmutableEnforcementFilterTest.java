/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.connectivity;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.model.connectivity.ImmutableEnforcementFilter}.
 */
public class ImmutableEnforcementFilterTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableEnforcementFilter.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(Enforcement.class, Placeholder.class).areAlsoImmutable(),
                assumingFields("filterPlaceholders").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableEnforcementFilter.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSimplePlaceholderPrefixed() {
        testSimplePlaceholder(
                "some/other/topic/{{  test:placeholder  }}",
                "some/other/topic/{{ thing:id }}",
                "eclipse:ditto",
                ThingId.of("eclipse:ditto"));
    }

    @Test
    public void testSimplePlaceholderPostfixed() {
        testSimplePlaceholder(
                "{{  test:placeholder  }}/some/topic/",
                "{{ thing:id }}/some/topic/",
                "eclipse:ditto",
                ThingId.of("eclipse:ditto"));
    }

    @Test
    public void testSimpleThingIdPlaceholderAndEntityIdFilter() {
        testSimplePlaceholder(
                "some/{{  test:placeholder  }}/topic",
                "some/{{ entity:id }}/topic",
                "eclipse:ditto",
                ThingId.of("eclipse:ditto"));
    }

    @Test
    public void testSimplePolicyIdPlaceholderAndEntityIdFilter() {
        testSimplePlaceholder(
                "some/{{  test:placeholder  }}/topic",
                "some/{{ entity:id }}/topic",
                "eclipse:ditto",
                PolicyId.of("eclipse:ditto"));
    }

    @Test
    public void testSimpleEntityIdPlaceholderAndEntityIdFilter() {
        testSimplePlaceholder(
                "some/{{  test:placeholder  }}/topic",
                "some/{{ entity:id }}/topic",
                "eclipse:ditto",
                DefaultNamespacedEntityId.of("eclipse:ditto"));
    }

    @Test
    public void testSimplePlaceholderPreAndPostfix() {
        testSimplePlaceholder(
                "some/topic/{{  test:placeholder  }}/topic",
                "some/topic/{{ thing:id }}/topic",
                "eclipse:ditto",
                ThingId.of("eclipse:ditto"));
    }

    @Test(expected = ConnectionSignalIdEnforcementFailedException.class)
    public void testSimplePlaceholderPreAndPostfixFails() {
        testSimplePlaceholder(
                "some/topic/{{  test:placeholder  }}/topic",
                "some/topic/{{ thing:id }}/topic",
                "eclipse:ditto",
                ThingId.of("ditto:eclipse"));
    }

    @Test(expected = UnresolvedPlaceholderException.class)
    public void testSimplePlaceholderWithUnresolvableInputPlaceholder() {
        testSimplePlaceholder(
                "{{  header:thing-id }}",
                "{{ thing:id }}",
                "eclipse:ditto",
                ThingId.of("eclipse:ditto"));
    }

    @Test(expected = UnresolvedPlaceholderException.class)
    public void testSimplePlaceholderWithUnresolvableMatcherPlaceholder() {
        testSimplePlaceholder(
                "{{  test:placeholder }}",
                "{{ some:id }}",
                "eclipse:ditto",
                ThingId.of("eclipse:ditto"));
    }

    @Test
    public void testDeviceIdHeaderMatchesThingId() {
        testDeviceIdHeaderEnforcement("thing", ThingId.of("eclipse:ditto"));
    }

    @Test
    public void testDeviceIdHeaderMatchesPolicyId() {
        testDeviceIdHeaderEnforcement("policy", PolicyId.of("eclipse:ditto"));
    }

    @Test
    public void testDeviceIdHeaderMatchesEntityId() {
        testDeviceIdHeaderEnforcement("entity", DefaultNamespacedEntityId.of("eclipse:ditto"));
    }

    public void testDeviceIdHeaderEnforcement(final String prefix, final NamespacedEntityId namespacedEntityId) {
        final HashMap<String, String> map = new HashMap<>();
        map.put("device_id", "eclipse:ditto");
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement("{{ header:device_id }}",
                "{{ " + prefix + ":name }}", // does not match
                "{{ " + prefix + ":id }}");  // matches
        final EnforcementFilterFactory<Map<String, String>, CharSequence> enforcementFilterFactory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement,
                        PlaceholderFactory.newHeadersPlaceholder());
        final EnforcementFilter<CharSequence> enforcementFilter = enforcementFilterFactory.getFilter(map);
        enforcementFilter.match(namespacedEntityId, DittoHeaders.empty());
    }

    private void testSimplePlaceholder(final String inputTemplate, final String filterTemplate,
            final String inputValue, final CharSequence filterValue) {
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement(inputTemplate, filterTemplate);
        final EnforcementFilterFactory<String, CharSequence> enforcementFilterFactory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement, SimplePlaceholder.INSTANCE);
        final EnforcementFilter<CharSequence> enforcementFilter = enforcementFilterFactory.getFilter(inputValue);
        enforcementFilter.match(filterValue, DittoHeaders.empty());
    }
}
