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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import java.util.List;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.GatewayConfig;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * A provider for {@link AuthorizationSubject}s contained in a {@link JsonWebToken}.
 */
public abstract class JwtAuthorizationSubjectsProvider implements Extension {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    protected final ActorSystem actorSystem;

    protected JwtAuthorizationSubjectsProvider(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Returns the {@code AuthorizationSubjects} of the given {@code JsonWebToken}.
     *
     * @param jsonWebToken the token containing the authorization subjects.
     * @return the authorization subjects.
     * @throws NullPointerException if {@code jsonWebToken} is {@code null}.
     */
    public abstract List<AuthorizationSubject> getAuthorizationSubjects(JsonWebToken jsonWebToken);

    public static JwtAuthorizationSubjectsProvider get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    private static final class ExtensionId extends AbstractExtensionId<JwtAuthorizationSubjectsProvider> {

        @Override
        public JwtAuthorizationSubjectsProvider createExtension(final ExtendedActorSystem system) {
            final GatewayConfig gatewayConfig =
                    DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(
                            system.settings().config()));

            return AkkaClassLoader.instantiate(system, JwtAuthorizationSubjectsProvider.class,
                    gatewayConfig.getAuthenticationConfig()
                            .getOAuthConfig()
                            .getJwtAuthorizationSubjectsProvider(),
                    List.of(ActorSystem.class, JwtSubjectIssuersConfig.class),
                    List.of(system, JwtSubjectIssuersConfig.fromOAuthConfig(
                            gatewayConfig.getAuthenticationConfig().getOAuthConfig())));
        }
    }

}
