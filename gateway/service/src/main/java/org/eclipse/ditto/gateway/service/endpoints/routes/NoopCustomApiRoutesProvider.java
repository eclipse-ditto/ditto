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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;

@Immutable
public final class NoopCustomApiRoutesProvider implements CustomApiRoutesProvider {

    private static final Route EMPTY_ROUTE = Directives.reject();

    public NoopCustomApiRoutesProvider(final ActorSystem actorSystem, final Config config) {
        // No-Op because Extensions need to have constructor accepting the actorSystem.
    }

    @Override
    public Route unauthorized(final RouteBaseProperties routeBaseProperties, final JsonSchemaVersion apiVersion,
            final CharSequence correlationId) {

        return EMPTY_ROUTE;
    }

    @Override
    public Route authorized(final RouteBaseProperties routeBaseProperties, final DittoHeaders dittoHeaders) {
        return EMPTY_ROUTE;
    }

}
