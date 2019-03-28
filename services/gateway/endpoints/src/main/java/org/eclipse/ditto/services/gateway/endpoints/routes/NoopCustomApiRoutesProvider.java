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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;

public final class NoopCustomApiRoutesProvider implements CustomApiRoutesProvider {
    private static final NoopCustomApiRoutesProvider INSTANCE = new NoopCustomApiRoutesProvider();
    private static final Route EMPTY_ROUTE = Directives.reject();

    private NoopCustomApiRoutesProvider() {}

    public static NoopCustomApiRoutesProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public Route unauthorized(final Integer apiVersion, final String correlationId) {
        return EMPTY_ROUTE;
    }

    @Override
    public Route authorized(final DittoHeaders dittoHeaders) {
        return EMPTY_ROUTE;
    }
}
