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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Ignore;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

/**
 * Tests {@link SearchNamespaceReportResult}.
 */
public final class SearchNamespaceReportResultTest {

    @Ignore("The class is immutable")
    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SearchNamespaceReportResult.class, //
                MutabilityMatchers.areEffectivelyImmutable(), //
                AllowedReason.assumingFields(
                        "searchNamespaceResultEntries").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void jsonTest() {
        final List<SearchNamespaceResultEntry> entries = new ArrayList<>();
        entries.add(new SearchNamespaceResultEntry("ns1", 4711));
        entries.add(new SearchNamespaceResultEntry("ns2", 8765));
        entries.add(new SearchNamespaceResultEntry("ns3", 815));

        final SearchNamespaceReportResult reportResult = new SearchNamespaceReportResult(entries);

        final JsonObject searchNamespaceReportResultJson = reportResult.toJson();
        final SearchNamespaceReportResult result =
                SearchNamespaceReportResult.fromJson(searchNamespaceReportResultJson);

        assertThat(result).isEqualTo(reportResult);
    }

}
