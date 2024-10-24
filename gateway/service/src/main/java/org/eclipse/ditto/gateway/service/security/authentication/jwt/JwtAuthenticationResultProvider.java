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

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import com.typesafe.config.Config;

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
     * @param extensionConfig the configuration for this extension.
     * @param suffix the optional suffix of the extension.
     * @return the {@code JwtAuthenticationResultProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static JwtAuthenticationResultProvider get(final ActorSystem actorSystem, final Config extensionConfig,
            @Nullable final String suffix) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(extensionConfig, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(extensionConfig, suffix);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, cfg -> new ExtensionId(suffix, cfg))
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<JwtAuthenticationResultProvider> {

        private static final String CONFIG_KEY = "jwt-authentication-result-provider";

        @Nullable private final String suffix;

        private ExtensionId(@Nullable final String suffix,
                final ExtensionIdConfig<JwtAuthenticationResultProvider> extensionIdConfig) {
            super(extensionIdConfig);
            this.suffix = suffix;
        }

        static ExtensionIdConfig<JwtAuthenticationResultProvider> computeConfig(final Config config,
                @Nullable final String suffix) {
            return ExtensionIdConfig.of(JwtAuthenticationResultProvider.class, config, buildConfigKey(suffix));
        }

        @Override
        protected String getConfigKey() {
            return buildConfigKey(suffix);
        }

        static String buildConfigKey(@Nullable final String suffix) {
            if (suffix != null) {
                return CONFIG_KEY + "-" + suffix;
            } else {
                return CONFIG_KEY;
            }
        }

    }

}
