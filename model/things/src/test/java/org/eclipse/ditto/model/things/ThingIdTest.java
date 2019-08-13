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
package org.eclipse.ditto.model.things;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.model.things.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ThingIdTest {

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(ThingId.class).verify();
    }

    @Test
    public void testEqualWithSameStringRepresentation() {
        assertThat((CharSequence) ThingId.of("test:test")).isNotEqualTo(ThingId.of("test:testx"));
    }

    @Test
    public void testNotEqualWithDifferentName() {
        assertThat((CharSequence) ThingId.of("test:test")).isNotEqualTo(ThingId.of("test:testx"));
    }

    @Test
    public void testNotEqualWithDifferentNamespace() {
        assertThat((CharSequence) ThingId.of("test:test")).isNotEqualTo(ThingId.of("testx:test"));
    }

    @Test
    public void testNotEqualToString() {
        assertThat((CharSequence) ThingId.of("test:test")).isNotEqualTo("test:test");
    }
}