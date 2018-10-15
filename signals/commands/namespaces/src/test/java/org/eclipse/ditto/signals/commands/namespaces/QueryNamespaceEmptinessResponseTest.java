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
package org.eclipse.ditto.signals.commands.namespaces;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public final class QueryNamespaceEmptinessResponseTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(QueryNamespaceEmptinessResponse.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(QueryNamespaceEmptinessResponse.class).usingGetClass().verify();
    }

//    @Test
//    public void serializeAsJson() {
//        final DittoHeaders headers = DittoHeaders.newBuilder().correlationId("x").build();
//        final QueryNamespaceEmptinessResponse expected = QueryNamespaceEmptinessResponse.newBuilder()
//                .resourceType("things")
//                .namespace("ns")
//                .isEmpty()
//                .dittoHeaders(headers)
//                .build();
//        final Object actual = QueryNamespaceEmptinessResponse.fromJson(expected.toJson(), headers);

//        assertThat(actual).isEqualTo(expected);
//    }

}
