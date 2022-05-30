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
package org.eclipse.ditto.connectivity.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.connectivity.model.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SignalEnforcementFilter}.
 */
public class SignalEnforcementFilterTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SignalEnforcementFilter.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(Enforcement.class, Placeholder.class).areAlsoImmutable(),
                assumingFields("filterPlaceholders").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SignalEnforcementFilter.class)
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
                new AbstractNamespacedEntityId(ThingConstants.ENTITY_TYPE, "eclipse:ditto") {});
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
        testDeviceIdHeaderEnforcement("entity",
                new AbstractNamespacedEntityId(ThingConstants.ENTITY_TYPE, "eclipse:ditto") {});
    }

    public void testDeviceIdHeaderEnforcement(final String prefix, final NamespacedEntityId namespacedEntityId) {
        final HashMap<String, String> map = new HashMap<>();
        map.put("device_id", "eclipse:ditto");
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement("{{ header:device_id }}",
                "{{ " + prefix + ":name }}", // does not match
                "{{ " + prefix + ":id }}");  // matches
        final EnforcementFilterFactory<Map<String, String>, Signal<?>> enforcementFilterFactory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement,
                        PlaceholderFactory.newHeadersPlaceholder());
        final EnforcementFilter<Signal<?>> enforcementFilter = enforcementFilterFactory.getFilter(map);
        final Signal<?> signal = mockSignalWithId(namespacedEntityId);
        enforcementFilter.match(signal);
    }

    private void testSimplePlaceholder(final String inputTemplate, final String filterTemplate,
            final String inputValue, final NamespacedEntityId namespacedEntityId) {
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement(inputTemplate, filterTemplate);
        final EnforcementFilterFactory<String, Signal<?>> enforcementFilterFactory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement, SimplePlaceholder.INSTANCE);
        final EnforcementFilter<Signal<?>> enforcementFilter = enforcementFilterFactory.getFilter(inputValue);
        final Signal<?> signal = mockSignalWithId(namespacedEntityId);
        enforcementFilter.match(signal);
    }

    private static Signal<?> mockSignalWithId(final NamespacedEntityId namespacedEntityId) {
        final SignalWithEntityId<?> signal = mock(SignalWithEntityId.class);
        when(signal.getEntityId()).thenReturn(namespacedEntityId);
        when(signal.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        return signal;
    }

}
