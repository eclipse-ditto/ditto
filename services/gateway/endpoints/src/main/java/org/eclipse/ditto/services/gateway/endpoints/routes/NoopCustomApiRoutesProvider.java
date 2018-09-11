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
