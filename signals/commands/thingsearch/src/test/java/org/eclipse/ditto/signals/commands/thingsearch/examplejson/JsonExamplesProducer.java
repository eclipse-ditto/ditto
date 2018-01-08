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
package org.eclipse.ditto.signals.commands.thingsearch.examplejson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchQuery;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.signals.commands.thingsearch.SearchErrorResponse;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidFilterException;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;

public final class JsonExamplesProducer {

    public static void main(final String... args) throws IOException {
        run(args, new JsonExamplesProducer());
    }

    private static void run(final String[] args, final JsonExamplesProducer producer) throws
            IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: the target folder in which to generate the JSON files");
            System.exit(-1);
        }
        produce(Paths.get(args[0]));
    }

    private static void produce(final Path rootPath) throws IOException {
        produceSearchModel(rootPath.resolve("search"));
        produceSearchCommands(rootPath.resolve("search"));
        produceSearchCommandResponses(rootPath.resolve("search"));
        produceSearchExceptions(rootPath.resolve("search"));
    }

    private static void produceSearchModel(final Path rootPath) throws IOException {
        final Path modelDir = rootPath.resolve(Paths.get("model"));
        Files.createDirectories(modelDir);

        final Thing thing = ThingsModelFactory.newThingBuilder().setId("default:thing1")
                .setAttribute(JsonFactory.newPointer("temperature"), JsonFactory.newValue(35L)).build();
        final Thing thing2 = ThingsModelFactory.newThingBuilder().setId("default:thing2")
                .setAttribute(JsonFactory.newPointer("temperature"), JsonFactory.newValue(35L)).build();
        final JsonArray items = JsonFactory.newArrayBuilder().add(thing.toJson(), thing2.toJson()).build();
        writeJson(modelDir.resolve(Paths.get("search-model.json")),
                SearchModelFactory.newSearchResultBuilder().addAll(items).build());
    }

    private static void produceSearchCommands(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("commands"));
        Files.createDirectories(commandsDir);

        final Set<String> knownNamespaces = new HashSet<>(Collections.singletonList("org.eclipse.ditto"));

        final SearchQuery searchQuery =
                SearchModelFactory.newSearchQueryBuilder(SearchModelFactory.property("attributes/temperature").eq(32))
                        .limit(0, 10).build();

        final QueryThings queryThingsCommand = QueryThings.of(searchQuery.getFilterAsString(),
                Collections.singletonList(searchQuery.getOptionsAsString()),
                JsonFactory.newFieldSelector("attributes", JsonFactory.newParseOptionsBuilder()
                        .withoutUrlDecoding()
                        .build()),
                knownNamespaces,
                DittoHeaders.empty());

        writeJson(commandsDir.resolve(Paths.get("query-things-command.json")), queryThingsCommand);

        final CountThings countThingsCommand = CountThings.of(searchQuery.getFilterAsString(), knownNamespaces,
                DittoHeaders.empty());

        writeJson(commandsDir.resolve(Paths.get("count-things-command.json")), countThingsCommand);
    }

    private static void produceSearchCommandResponses(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("responses"));
        Files.createDirectories(commandsDir);


        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId("default:thing1")
                .setAttribute(JsonFactory.newPointer("temperature"), JsonFactory.newValue(35L))
                .build();
        final Thing thing2 = ThingsModelFactory.newThingBuilder()
                .setId("default:thing2")
                .setAttribute(JsonFactory.newPointer("temperature"), JsonFactory.newValue(35L))
                .build();
        final JsonArray array = JsonFactory.newArrayBuilder()
                .add(thing.toJson())
                .add(thing2.toJson())
                .build();

        final SearchResult result = SearchModelFactory.newSearchResult(array, -1L);
        final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(result, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("query-things-response.json")), queryThingsResponse);

        final CountThingsResponse countThingsResponse = CountThingsResponse.of(42, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("count-things-response.json")), countThingsResponse);

        final DittoRuntimeException e =
                DittoRuntimeException.newBuilder("search.filter.invalid", HttpStatusCode.BAD_REQUEST)
                        .build();
        final SearchErrorResponse errorResponse = SearchErrorResponse.of(e, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("query-things-error-response.json")), errorResponse);
    }

    private static void produceSearchExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("errors"));
        Files.createDirectories(exceptionsDir);

        final InvalidFilterException invalidFilterException = InvalidFilterException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("invalidFilterException.json")), invalidFilterException);

        final InvalidOptionException invalidOptionException = InvalidOptionException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("invalidOptionException.json")), invalidOptionException);
    }

    private static void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        writeJson(path, jsonifiable, JsonSchemaVersion.LATEST);
    }

    private static void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final JsonSchemaVersion schemaVersion) throws IOException {

        final String jsonString = jsonifiable.toJsonString(schemaVersion);
        System.out.println("Writing file: " + path.toAbsolutePath());
        Files.write(path, jsonString.getBytes());
    }

}
