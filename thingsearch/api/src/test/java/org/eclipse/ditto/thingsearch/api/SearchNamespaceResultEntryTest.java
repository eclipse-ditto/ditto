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
package org.eclipse.ditto.thingsearch.api;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

/**
 * Tests {@link SearchNamespaceResultEntry}.
 */
public class SearchNamespaceResultEntryTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SearchNamespaceResultEntry.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void jsonTest() {
        final SearchNamespaceResultEntry searchNamespaceResultEntry = new SearchNamespaceResultEntry("ns1", 4711);

        final JsonObject searchNamespaceResultEntryJson = searchNamespaceResultEntry.toJson();
        final SearchNamespaceResultEntry result = SearchNamespaceResultEntry.fromJson(searchNamespaceResultEntryJson);

        Assertions.assertThat(result).isEqualTo(searchNamespaceResultEntry);
    }
}
