/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

import akka.http.javadsl.server.RequestContext;

/**
 * A default CustomHeadersHandler implementation which does not adjust the headers.
 */
public final class NoopCustomHeadersHandler implements CustomHeadersHandler {

    private static final NoopCustomHeadersHandler INSTANCE = new NoopCustomHeadersHandler();

    private NoopCustomHeadersHandler() {}

    public static NoopCustomHeadersHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public CompletionStage<DittoHeaders> handleCustomHeaders(final CharSequence correlationId,
            final RequestContext requestContext,
            final RequestType requestType,
            final DittoHeaders dittoDefaultHeaders) {

        return CompletableFuture.completedFuture(dittoDefaultHeaders);
    }
}
