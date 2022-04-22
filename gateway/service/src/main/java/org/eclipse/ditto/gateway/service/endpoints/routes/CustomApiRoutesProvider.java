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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;
import akka.http.javadsl.server.Route;

/**
 * Provider for custom routes.
 * You can distinguish between routes for unauthorized access and authorized access.
 */
public abstract class CustomApiRoutesProvider extends DittoExtensionPoint {

    protected CustomApiRoutesProvider(final ActorSystem actorSystem) {
        super(actorSystem);
    }

    /**
     * Provides a custom route for unauthorized access.
     *
     * @param routeBaseProperties the basic properties of the root route.
     * @param version the API version.
     * @param correlationId the correlation ID.
     * @return custom route for unauthorized access.
     */
    public abstract Route unauthorized(RouteBaseProperties routeBaseProperties, JsonSchemaVersion version,
            CharSequence correlationId);

    /**
     * Provides a custom route for authorized access.
     *
     * @param routeBaseProperties the basic properties of the root route.
     * @param headers headers of the request.
     * @return custom route for authorized access.
     */
    public abstract Route authorized(RouteBaseProperties routeBaseProperties, DittoHeaders headers);

    /**
     * Loads the implementation of {@code CustomApiRoutesProvider} which is configured for the {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code CustomApiRoutesProvider} should be loaded.
     * @return the {@code CustomApiRoutesProvider} implementation.
     */
    public static CustomApiRoutesProvider get(final ActorSystem actorSystem) {
        final var implementation = DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(
                actorSystem.settings().config())).getHttpConfig().getCustomApiRoutesProvider();

        return new ExtensionId<>(implementation, CustomApiRoutesProvider.class).get(actorSystem);
    }

}
