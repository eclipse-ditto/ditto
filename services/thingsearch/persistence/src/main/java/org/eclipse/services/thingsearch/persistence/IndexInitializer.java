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
package org.eclipse.services.thingsearch.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.Success;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;

import akka.event.Logging;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Initializes indices on a passed in mongoDB collection.
 */
public final class IndexInitializer {

    /**
     * prefix for all fields which are indexed.
     */
    private static final String INDEX_PREFIX = "ditto.things-search.indices.collections";
    private static final String COMMA = ",";
    private static final String INDEX_ENABLED = "ditto.things-search.indices.enabled";

    private static final Logger LOG = LoggerFactory.getLogger(IndexInitializer.class);


    private IndexInitializer() {
    }

    /**
     * Creates indices on the passed in {@code collection}. The indices are read from the properties file with the
     * passed in {@code propertiesName}.
     *
     * @param collection the name of the collection on which the index must be created
     * @param collectionPrefix the prefix of the collection in the properties file.
     * @param config the Config from which the needed index values are read
     * @param materializer the materializer to run akka streams with
     */
    public static void initializeIndices(final MongoCollection<Document> collection, final String collectionPrefix,
            final Config config, final Materializer materializer) {

        final Map<String, String> expectedIndexes = readExpectedIndexes(config, collectionPrefix);

        final boolean indexEnabled = config.getBoolean(INDEX_ENABLED);
        if (!indexEnabled) {
            LOG.info("index creation disabled");
            return;
        }

        LOG.info("{} indexes expected: {}", expectedIndexes.size(), expectedIndexes);

        Source.fromPublisher(collection.listIndexes()) //
                .map(doc -> doc.getString("name")) //
                .filter(k -> k.startsWith(collectionPrefix)) //
                .fold(new ArrayList<String>(), (list, k) -> {
                    list.add(k);
                    return list;
                }) //
                .log("existing-indexes") //
                .withAttributes(Attributes.createLogLevels(Logging.InfoLevel(), Logging.DebugLevel(), Logging
                        .WarningLevel())) //
                .flatMapConcat(actualIndexes -> Source.from(actualIndexes) //
                        .filter(name -> !expectedIndexes.containsKey(name)) //
                        .log("drop-index") //
                        .withAttributes(Attributes.createLogLevels(Logging.InfoLevel(),
                                Logging.DebugLevel(), Logging.WarningLevel())) //
                        .flatMapConcat(name ->
                                Source.fromPublisher(collection.dropIndex(name)) //
                                        .map(Success::toString)) //
                        .merge( //
                                Source.from(expectedIndexes.keySet()) //
                                        .filter(name -> !actualIndexes.contains(name)) //
                                        .flatMapConcat(name ->
                                                Source.fromPublisher(collection.createIndex(
                                                        createIndexBson(expectedIndexes.get(name)),
                                                        new IndexOptions().name(name).background(true)))
                                                        .log("create-index") //
                                                        .withAttributes(Attributes.createLogLevels(Logging.InfoLevel(),
                                                                Logging.DebugLevel(), Logging.WarningLevel())) //
                                        ) //
                        ))
                .runWith(Sink.ignore(), materializer);
    }

    private static Map<String, String> readExpectedIndexes(final Config config, final String collectionPrefix) {
        final ConfigObject indicesCollections = config.getObject(INDEX_PREFIX);

        return indicesCollections.keySet().stream().filter(k -> k.equals(collectionPrefix))
                .collect(HashMap<String, String>::new,
                        (acc, k) -> {
                            final Map<String, String> indices = (Map<String, String>) indicesCollections.get(k)
                                    .unwrapped();
                            indices.forEach((key, value) -> acc.put(k + "." + key, value));
                        },
                        Map::putAll);
    }

    private static Bson createIndexBson(final String value) {
        final Document indexes = new Document();
        Arrays.stream(value.split(COMMA)).forEach(s -> indexes.append(s, 1));
        return indexes;
    }
}
