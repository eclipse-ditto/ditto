/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.models.thingsearch;

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
