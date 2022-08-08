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
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Responsible for extraction of an {@link org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult} out of a
 * {@link JsonWebToken JSON web token}.
 */
public interface JwtAuthenticationResultProvider extends DittoExtensionPoint {

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
     * @param config the configuration for this extension.
     * @return the {@code JwtAuthenticationResultProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static JwtAuthenticationResultProvider get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<JwtAuthenticationResultProvider> {

        private static final String CONFIG_KEY = "jwt-authentication-result-provider";

        private ExtensionId(final ExtensionIdConfig<JwtAuthenticationResultProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<JwtAuthenticationResultProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(JwtAuthenticationResultProvider.class,config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
