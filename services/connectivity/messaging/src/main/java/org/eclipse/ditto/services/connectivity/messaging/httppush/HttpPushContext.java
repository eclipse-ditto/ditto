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
package org.eclipse.ditto.services.connectivity.messaging.httppush;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

import akka.http.javadsl.model.Uri;

/**
 * Context which passes through the {@link ExternalMessage} to publish to an HTTP endpoint and the {@code requestUri}
 * used for logging into the user logs at later stages in the stream where the HTTP request is no longer available.
 */
@Immutable
final class HttpPushContext {

    private final ExternalMessage externalMessage;
    private final Uri requestUri;

    HttpPushContext(final ExternalMessage externalMessage, final Uri requestUri) {
        this.externalMessage = externalMessage;
        this.requestUri = requestUri;
    }

    ExternalMessage getExternalMessage() {
        return externalMessage;
    }

    Uri getRequestUri() {
        return requestUri;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpPushContext that = (HttpPushContext) o;
        return Objects.equals(externalMessage, that.externalMessage) &&
                Objects.equals(requestUri, that.requestUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalMessage, requestUri);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "externalMessage=" + externalMessage +
                ", requestUri=" + requestUri +
                "]";
    }
}
