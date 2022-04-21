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

import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.GatewayConfig;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.http.javadsl.server.Route;

/**
 * Provider for custom routes.
 * You can distinguish between routes for unauthorized access and authorized access.
 */
public abstract class CustomApiRoutesProvider implements Extension {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    protected final ActorSystem actorSystem;

    protected CustomApiRoutesProvider(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public abstract Route unauthorized(RouteBaseProperties routeBaseProperties, JsonSchemaVersion version, CharSequence correlationId);

    public abstract Route authorized(RouteBaseProperties routeBaseProperties, DittoHeaders headers);

    public static CustomApiRoutesProvider get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    private static final class ExtensionId extends AbstractExtensionId<CustomApiRoutesProvider> {

        @Override
        public CustomApiRoutesProvider createExtension(final ExtendedActorSystem system) {
            final GatewayConfig gatewayConfig =
                    DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(
                            system.settings().config()));

            return AkkaClassLoader.instantiate(system, CustomApiRoutesProvider.class,
                    gatewayConfig.getHttpConfig().getCustomApiRoutesProvider(),
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }

}
