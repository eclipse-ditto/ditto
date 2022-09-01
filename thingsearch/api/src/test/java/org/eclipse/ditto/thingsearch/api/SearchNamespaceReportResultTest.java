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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Tests {@link SearchNamespaceReportResult}.
 */
public final class SearchNamespaceReportResultTest {

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
