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
package org.eclipse.ditto.model.base.headers;

import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;

/**
 * Interface for reading Ditto headers from external headers and writing Ditto headers to external headers.
 */
public interface HeaderPublisher {

    /**
     * Read Ditto headers from external headers.
     *
     * @param externalHeaders external headers as a map.
     * @return Ditto headers initialized with values from external headers.
     */
    DittoHeaders fromExternalHeaders(final Map<String, String> externalHeaders);

    /**
     * Publish Ditto headers to external headers.
     *
     * @param dittoHeaders Ditto headers to publish.
     * @return external headers.
     */
    Map<String, String> toExternalHeaders(final DittoHeaders dittoHeaders);

    default DittoHeaders fromExternalJson(final JsonObject externalHeaders) {
        return fromExternalHeaders(externalHeaders.stream()
                .collect(Collectors.toMap(JsonField::getKeyName, field -> field.getValue().formatAsString())));
    }

    default JsonObject toExternalJson(final DittoHeaders dittoHeaders) {
        return toExternalHeaders(dittoHeaders).entrySet()
                .stream()
                .map(entry -> JsonFactory.newField(
                        JsonFactory.newKey(entry.getKey()),
                        JsonFactory.newValue(entry.getValue())))
                .collect(JsonCollectors.fieldsToObject());
    }

    /**
     * Provider of a header publisher.
     */
    interface Provider {

        HeaderPublisher get();
    }
}
