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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import akka.actor.ActorSystem;

/**
 * Responsible for extraction of an {@link org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult} out of a
 * {@link JsonWebToken JSON web token}.
 */
public interface JwtAuthenticationResultProvider extends DittoExtensionPoint {

    String CONFIG_PATH = "ditto.gateway.authentication.oauth.jwt-authentication-result-provider";

    /**
     * Extracts an {@code AuthenticationResult} out of a given JsonWebToken.
     *
     * @param jwt the JSON web token that contains the information to be extracted into an authorization context.
     * @param dittoHeaders the DittoHeaders to use for the extracted authentication result.
     * @return the authentication result based on the given JSON web token.
     * @throws NullPointerException if any argument is {@code null}.
     */
    CompletionStage<JwtAuthenticationResult> getAuthenticationResult(JsonWebToken jwt,
            DittoHeaders dittoHeaders);

    /**
     * Loads the implementation of {@code JwtAuthenticationResultProvider} which is configured for the {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code JwtAuthenticationResultProvider} should be loaded.
     * @return the {@code JwtAuthenticationResultProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static JwtAuthenticationResultProvider get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = actorSystem.settings().config().getString(CONFIG_PATH);
        return new ExtensionId<>(implementation, JwtAuthenticationResultProvider.class).get(actorSystem);
    }

}
