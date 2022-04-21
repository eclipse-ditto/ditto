/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.placeholders;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.signals.WithResource;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link org.eclipse.ditto.protocol.placeholders.ImmutableResourcePlaceholder}.
 */
public final class ImmutableResourcePlaceholderTest {

    private static final JsonPointer KNOWN_JSON_POINTER_ROOT = JsonPointer.of("/");
    private static final String KNOWN_RESOURCE_TYPE_POLICY = "policy";
    private static final WithResource KNOWN_WITH_RESOURCE_ROOT = new WithResource() {
        @Override
        public JsonPointer getResourcePath() {
            return KNOWN_JSON_POINTER_ROOT;
        }

        @Override
        public String getResourceType() {
            return KNOWN_RESOURCE_TYPE_POLICY;
        }
    };

    private static final JsonPointer KNOWN_JSON_POINTER_ATTRIBUTE = JsonPointer.of("/attributes/test");
    private static final String KNOWN_RESOURCE_TYPE_THING = "thing";
    private static final WithResource KNOWN_WITH_RESOURCE_ATTRIBUTE = new WithResource() {
        @Override
        public JsonPointer getResourcePath() {
            return KNOWN_JSON_POINTER_ATTRIBUTE;
        }

        @Override
        public String getResourceType() {
            return KNOWN_RESOURCE_TYPE_THING;
        }
    };

    private static final ImmutableResourcePlaceholder UNDER_TEST = ImmutableResourcePlaceholder.INSTANCE;

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableResourcePlaceholder.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableResourcePlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testReplaceTypePolicy() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_WITH_RESOURCE_ROOT, "type"))
                .contains(KNOWN_RESOURCE_TYPE_POLICY);
    }

    @Test
    public void testReplacePath() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_WITH_RESOURCE_ROOT, "path"))
                .contains(KNOWN_JSON_POINTER_ROOT.toString());
    }

    @Test
    public void testReplaceTypeThing() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_WITH_RESOURCE_ATTRIBUTE, "type"))
                .contains(KNOWN_RESOURCE_TYPE_THING);
    }

    @Test
    public void testReplacePathThing() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_WITH_RESOURCE_ATTRIBUTE, "path"))
                .contains(KNOWN_JSON_POINTER_ATTRIBUTE.toString());
    }

}
