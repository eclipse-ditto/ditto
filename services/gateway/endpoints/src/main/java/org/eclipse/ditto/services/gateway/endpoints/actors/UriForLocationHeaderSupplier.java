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

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Uri;

/**
 * Returns the URI for the location header for HTTP responses when a resource was created.
 */
final class UriForLocationHeaderSupplier implements Supplier<Uri> {

    private final HttpRequest httpRequest;
    private final CommandResponse<?> commandResponse;

    UriForLocationHeaderSupplier(final HttpRequest httpRequest, final CommandResponse<?> commandResponse) {
        this.httpRequest = httpRequest;
        this.commandResponse = commandResponse;
    }

    @Override
    public Uri get() {
        final Uri requestUri = httpRequest.getUri();
        if (isRequestIdempotent()) {
            return requestUri;
        }
        return Uri.create(removeTrailingSlash(getLocationUriString(removeEntityId(requestUri.toString()))));
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
        final EntityId entityId = commandResponse.getEntityId();
        return requestUri.indexOf(entityId.toString());
    }

    private static String removeTrailingSlash(final String createdLocationUri) {
        if (createdLocationUri.endsWith("/")) {
            return createdLocationUri.substring(0, createdLocationUri.length() - 1);
        }
        return createdLocationUri;
    }

    private String getLocationUriString(final String requestUri) {
        return requestUri + "/" + commandResponse.getEntityId() + commandResponse.getResourcePath();
    }

}
