/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.Index;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.thingsearch.service.persistence.Indices;

/**
 * Package-private interface of configured hints for MongoDB.
 */
interface MongoHints {

    /**
     * Get a hint for the namespaces of a search query if any is configured.
     *
     * @param namespaces namespaces of a search query or null if none exists.
     * @return the hint configured for one of the namespaces if any exists.
     */
    Optional<Bson> getHint(@Nullable Set<String> namespaces);

    /**
     * @return no hints for any namespace.
     */
    static MongoHints empty() {
        return new Empty();
    }

    /**
     * Extract hints from a JSON representation.
     *
     * @param jsonString text of a JSON object mapping namespaces to MongoDB hints.
     * @return the extracted hints.
     */
    static MongoHints byNamespace(final String jsonString) {
        return new ByNamespace(jsonString);
    }

    final class Empty implements MongoHints {

        private Empty() {}

        @Override
        public Optional<Bson> getHint(@Nullable final Set<String> namespaces) {
            return Optional.empty();
        }
    }

    final class ByNamespace implements MongoHints {

        private final Map<String, Bson> map;

        private ByNamespace(final String jsonString) {
            map = JsonObject.of(jsonString)
                    .stream()
                    .collect(Collectors.toMap(JsonField::getKeyName, ByNamespace::fieldToBson));
        }

        @Override
        public Optional<Bson> getHint(@Nullable final Set<String> namespaces) {
            if (namespaces != null) {
                return namespaces.stream().filter(map::containsKey).map(map::get).findAny();
            } else {
                return Optional.empty();
            }
        }

        private static Bson fieldToBson(final JsonField field) {
            final JsonValue value = field.getValue();
            if (value.isString()) {
                final Optional<Index> index = getIndexByName(value.asString());
                return index.map(Index::getKeys).orElse(null);
            } else {
                // it is an error if the configured hint is neither an index name nor an index spec as document.
                final JsonObject jsonObject = value.asObject();

                return DittoBsonJson.getInstance().parse(jsonObject);
            }
        }

        private static Optional<Index> getIndexByName(final String name) {
            return Indices.all().stream().filter(index -> Objects.equals(name, index.getName())).findAny();
        }
    }
}
