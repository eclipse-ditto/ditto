/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity.placeholder;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutableEnforcementFilter}.
 */
public class ImmutableEnforcementFilterTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableEnforcementFilter.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(Enforcement.class, Placeholder.class).areAlsoImmutable());
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
                "eclipse:ditto");
    }

    @Test
    public void testSimplePlaceholderPostfixed() {
        testSimplePlaceholder(
                "some/other/topic/{{  test:placeholder  }}",
                "some/other/topic/{{ thing:id }}",
                "eclipse:ditto",
                "eclipse:ditto");
    }

    @Test
    public void testSimplePlaceholderPreAndPostfix() {
        testSimplePlaceholder(
                "some/topic/{{  test:placeholder  }}/topic",
                "some/topic/{{ thing:id }}/topic",
                "eclipse:ditto",
                "eclipse:ditto");
    }

    @Test(expected = ConnectionSignalIdEnforcementFailedException.class)
    public void testSimplePlaceholderPreAndPostfixFails() {
        testSimplePlaceholder(
                "some/topic/{{  test:placeholder  }}/topic",
                "some/topic/{{ thing:id }}/topic",
                "eclipse:ditto",
                "ditto:eclipse");
    }

    @Test(expected = UnresolvedPlaceholderException.class)
    public void testSimplePlaceholderWithUnresolvableInputPlaceholder() {
        testSimplePlaceholder(
                "{{  header:thing-id }}",
                "{{ thing:id }}",
                "eclipse:ditto",
                "eclipse:ditto");
    }

    @Test(expected = UnresolvedPlaceholderException.class)
    public void testSimplePlaceholderWithUnresolvableMatcherPlaceholder() {
        testSimplePlaceholder(
                "{{  header:thing-id }}",
                "{{ thing:id }}",
                "eclipse:ditto",
                "eclipse:ditto");
    }

    @Test
    public void testDeviceIdHeaderMatchesThingId() {
        final HashMap<String, String> map = new HashMap<>();
        map.put("device_id", "eclipse:ditto");
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement("{{ header:device_id }}",
                "{{ thing:name }}", // does not match
                "{{ thing:id }}");  // matches
        final EnforcementFilterFactory<Map<String, String>, String> enforcementFilterFactory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement,
                        PlaceholderFactory.newHeadersPlaceholder());
        final EnforcementFilter<String> enforcementFilter = enforcementFilterFactory.getFilter(map);
        enforcementFilter.match("eclipse:ditto", DittoHeaders.empty());
    }

    private void testSimplePlaceholder(final String inputTemplate, final String filterTemplate,
            final String inputValue, final String filterValue) {
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement(inputTemplate, filterTemplate);
        final EnforcementFilterFactory<String, String> enforcementFilterFactory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement, SimplePlaceholder.INSTANCE);
        final EnforcementFilter<String> enforcementFilter = enforcementFilterFactory.getFilter(inputValue);
        enforcementFilter.match(filterValue, DittoHeaders.empty());
    }

}
