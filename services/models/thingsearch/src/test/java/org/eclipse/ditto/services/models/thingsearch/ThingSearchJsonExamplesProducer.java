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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchQuery;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoCountThings;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoRetrieveNamespaceReport;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoRetrieveNamespaceReportResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThingsResponse;

/**
 * Produces examples for the search model.
 */
public final class ThingSearchJsonExamplesProducer {

    public static void main(final String... args) throws IOException {
        produceSudoCommands(Paths.get(args[0]));
    }

    private static void produceSudoCommands(final Path rootPath) throws IOException {
        final Path sudoCommandsDir = rootPath.resolve(Paths.get("sudo"));
        Files.createDirectories(sudoCommandsDir);

        final SearchQuery searchQuery =
                SearchModelFactory.newSearchQueryBuilder(SearchModelFactory.property("attributes/temperature").eq(32))
                        .limit(0, 10).build();

        final SudoCountThings sudoCountThingsCommand =
                SudoCountThings.of(searchQuery.getFilterAsString(), DittoHeaders.empty());
        writeJson(sudoCommandsDir.resolve(Paths.get("sudo-count-things-command.json")),
                sudoCountThingsCommand.toJsonString());

        final CountThingsResponse sudoCountThingsResponse = CountThingsResponse.of(42, DittoHeaders.empty());
        writeJson(sudoCommandsDir.resolve(Paths.get("sudo-count-things-response.json")),
                sudoCountThingsResponse.toJsonString());

        final List<SearchNamespaceResultEntry> entries = new ArrayList<>();
        entries.add(new SearchNamespaceResultEntry("ns1", 4711));
        entries.add(new SearchNamespaceResultEntry("ns2", 8765));
        entries.add(new SearchNamespaceResultEntry("ns3", 815));
        final SearchNamespaceReportResult reportResult = new SearchNamespaceReportResult(entries);

        final SudoRetrieveNamespaceReport sudoRetrieveNamespaceReport =
                SudoRetrieveNamespaceReport.of(DittoHeaders.empty());
        writeJson(sudoCommandsDir.resolve(Paths.get("sudo-retrieve-namespace-report-command.json")),
                sudoRetrieveNamespaceReport.toJsonString());

        final SudoRetrieveNamespaceReportResponse sudoRetrieveNamespaceReportResponse =
                SudoRetrieveNamespaceReportResponse.of(reportResult, DittoHeaders.empty());
        writeJson(sudoCommandsDir.resolve(Paths.get("sudo-retrieve-namespace-report-response.json")),
                sudoRetrieveNamespaceReportResponse.toJsonString());
    }

    private static void writeJson(final Path path, final String json) throws IOException {
        System.out.println("Writing file: " + path.toAbsolutePath());
        Files.write(path, json.getBytes());
    }

}
