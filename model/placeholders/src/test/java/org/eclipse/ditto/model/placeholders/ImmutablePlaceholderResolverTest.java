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
package org.eclipse.ditto.model.placeholders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutablePlaceholderResolver}.
 */
public class ImmutablePlaceholderResolverTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutablePlaceholderResolver.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(Placeholder.class).isAlsoImmutable(),
                AllowedReason.assumingFields("placeholderSource").areNotModifiedAndDoNotEscape());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePlaceholderResolver.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testPlaceholderResolvementBasedOnMap() {
        final Map<String, String> inputMap = new HashMap<>();
        inputMap.put("one", "1");
        inputMap.put("two", "2");

        final ImmutablePlaceholderResolver<Map<String, String>> underTest = new ImmutablePlaceholderResolver<>(
                PlaceholderFactory.newHeadersPlaceholder(), inputMap, false);

        assertThat(underTest.resolve("one"))
                .contains("1");
        assertThat(underTest.resolve("two"))
                .contains("2");
    }

    @Test
    public void testPlaceholderResolvementBasedOnThingId() {
        final String thingId = "org.eclipse.ditto:foobar199";

        final ImmutablePlaceholderResolver<String> underTest = new ImmutablePlaceholderResolver<>(
                PlaceholderFactory.newThingPlaceholder(), thingId, false);

        assertThat(underTest.resolve("id"))
                .contains(thingId);
        assertThat(underTest.resolve("namespace"))
                .contains("org.eclipse.ditto");
        assertThat(underTest.resolve("name"))
                .contains("foobar199");
    }

    @Test
    public void testPlaceholderResolvementBasedOnTopic() {
        final String fullPath = "org.eclipse.ditto/foo23/things/twin/commands/modify";
        final TopicPath topic = ProtocolFactory.newTopicPath(fullPath);

        final ImmutablePlaceholderResolver<TopicPath> underTest = new ImmutablePlaceholderResolver<>(
                PlaceholderFactory.newTopicPathPlaceholder(), topic, false);

        assertThat(underTest.resolve("full"))
                .contains(fullPath);
        assertThat(underTest.resolve("namespace"))
                .contains("org.eclipse.ditto");
        assertThat(underTest.resolve("entityId"))
                .contains("foo23");
        assertThat(underTest.resolve("group"))
                .contains("things");
        assertThat(underTest.resolve("channel"))
                .contains("twin");
        assertThat(underTest.resolve("criterion"))
                .contains("commands");
        assertThat(underTest.resolve("action"))
                .contains("modify");
    }
}
