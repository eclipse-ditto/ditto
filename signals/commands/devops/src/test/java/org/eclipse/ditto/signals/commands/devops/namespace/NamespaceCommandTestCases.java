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
package org.eclipse.ditto.signals.commands.devops.namespace;

import static org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.reflect.Method;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests some {@link NamespaceCommand}.
 */
public abstract class NamespaceCommandTestCases<T extends NamespaceCommand> {

    abstract Class<T> classUnderTest();

    abstract NamespaceCommand<T> fromNamespace(final String namespace);

    @Test
    public void assertImmutability() {
        assertInstancesOf(classUnderTest(), areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(classUnderTest()).usingGetClass().verify();
    }

    @Test
    public void serializeAsJson() throws Exception {
        final Method fromJson = classUnderTest().getMethod("fromJson", JsonObject.class, DittoHeaders.class);

        final DittoHeaders headers = DittoHeaders.newBuilder().correlationId("x").build();
        final T expected = fromNamespace("ns").setDittoHeaders(headers);
        final Object actual = fromJson.invoke(null, expected.toJson(), headers);

        assertThat(actual).isEqualTo(expected);
    }
}
