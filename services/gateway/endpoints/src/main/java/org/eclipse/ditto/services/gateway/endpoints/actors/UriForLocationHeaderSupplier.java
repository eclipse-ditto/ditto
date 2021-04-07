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
package org.eclipse.ditto.services.gateway.endpoints.actors;

import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.entity.id.EntityId;

import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Query;
import akka.http.javadsl.model.Uri;

/**
 * Returns the URI for the location header for HTTP responses when a resource was created.
 */
final class UriForLocationHeaderSupplier implements Supplier<Uri> {

    private final HttpRequest httpRequest;
    private final EntityId entityId;
    private final JsonPointer resourcePath;

    UriForLocationHeaderSupplier(final HttpRequest httpRequest, final EntityId entityId, final JsonPointer resourcePath) {
        this.httpRequest = httpRequest;
        this.entityId = entityId;
        this.resourcePath = resourcePath;
    }

    @Override
    public Uri get() {
        final Uri requestUri = httpRequest.getUri().query(Query.EMPTY); // strip query params
        if (isRequestIdempotent()) {
            return requestUri.query(Query.EMPTY);
        }
        return Uri.create(removeTrailingSlash(getLocationUriString(removeEntityId(requestUri.toString()))))
                .query(Query.EMPTY);
    }

    private boolean isRequestIdempotent() {
        final HttpMethod requestMethod = httpRequest.method();
        return requestMethod.isIdempotent();
    }

    private String removeEntityId(final String requestUri) {
        final int uriIdIndex = getIndexOfEntityId(requestUri);
        if (0 < uriIdIndex) {
            return removeTrailingSlash(requestUri.substring(0, uriIdIndex));
        }
        return requestUri;
    }

    private int getIndexOfEntityId(final String requestUri) {
        return requestUri.indexOf(entityId.toString());
    }

    private static String removeTrailingSlash(final String createdLocationUri) {
        if (createdLocationUri.endsWith("/")) {
            return createdLocationUri.substring(0, createdLocationUri.length() - 1);
        }
        return createdLocationUri;
    }

    private String getLocationUriString(final String requestUri) {
        return requestUri + "/" + entityId + resourcePath;
    }

}
