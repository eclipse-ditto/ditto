/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.headers.metadata;

import java.util.SortedSet;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * A {@link SortedSet} of {@link MetadataHeader}s.
 * The sort order is determined by {@link MetadataHeader#compareTo(MetadataHeader)}.
 * Sorting metadata headers ensures that values for a key with specific path overwrite overlapping values for a key with
 * wildcard path, i. e. specific > generic.
 *
 * @since 1.2.0
 */
@NotThreadSafe
public interface MetadataHeaders extends SortedSet<MetadataHeader>, Jsonifiable<JsonArray> {

    /**
     * Creates a new instance of MetadataHeaders.
     *
     * @return the instance.
     */
    static MetadataHeaders newInstance() {
        return MetadataPackageFactory.newMetadataHeaders();
    }

    /**
     * Parses the CharSequence argument as an instance of MetadataHeaders.
     *
     * @param metadataHeadersCharSequence the CharSequence containing the MetadataHeaders JSON array
     * representation to be parsed. If the CharSequence is interpreted as an empty JSON array.
     * @return the instance.
     * @throws NullPointerException if {@code metadataHeadersCharSequence} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code metadataHeadersCharSequence} is not a JSON array or
     * if it contained an invalid JSON object representation of a MetadataHeader.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code metadataHeadersCharSequence} contained
     * a metadata JSON object with a missing field.
     * @see MetadataHeader#fromJson(JsonObject)
     */
    static MetadataHeaders parseMetadataHeaders(final CharSequence metadataHeadersCharSequence) {
        return MetadataPackageFactory.parseMetadataHeaders(metadataHeadersCharSequence);
    }

}
