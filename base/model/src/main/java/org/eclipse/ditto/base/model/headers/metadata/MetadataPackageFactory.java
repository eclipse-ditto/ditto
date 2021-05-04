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
package org.eclipse.ditto.base.model.headers.metadata;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Factory for the package-private classes of the "metadata" package.
 * This class only exists to hide specific implementations for interfaces and to decouple the interfaces from
 * their implementation classes.
 *
 * @since 1.2.0
 */
@Immutable
final class MetadataPackageFactory {

    private MetadataPackageFactory() {
        throw new AssertionError();
    }

    static DefaultMetadataHeader getMetadataHeader(final MetadataHeaderKey key, final JsonValue value) {
        return DefaultMetadataHeader.of(key, value);
    }

    static MetadataHeader metadataHeaderFromJson(final JsonObject jsonObject) {
        return DefaultMetadataHeader.fromJson(jsonObject);
    }

    static DefaultMetadataHeaderKey parseMetadataHeaderKey(final CharSequence key) {
        return DefaultMetadataHeaderKey.parse(key);
    }

    static DefaultMetadataHeaderKey getMetadataHeaderKey(final JsonPointer path) {
        return DefaultMetadataHeaderKey.of(path);
    }

    static MetadataHeaders parseMetadataHeaders(final CharSequence metadataHeaders) {
        return DefaultMetadataHeaders.parseMetadataHeaders(metadataHeaders);
    }

    static MetadataHeaders newMetadataHeaders() {
        return DefaultMetadataHeaders.newInstance();
    }

}
