/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.directives.auth;

import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationProvider;
import org.eclipse.ditto.gateway.service.util.config.security.DevOpsConfig;

import com.typesafe.config.Config;

public final class DevopsAuthenticationDirectiveFactory {

    private final JwtAuthenticationProvider jwtAuthenticationProvider;
    private volatile DevOpsConfig devOpsConfig;

    private DevopsAuthenticationDirectiveFactory(final JwtAuthenticationProvider jwtAuthenticationProvider,
            final DevOpsConfig devOpsConfig) {

        this.jwtAuthenticationProvider = jwtAuthenticationProvider;
        this.devOpsConfig = devOpsConfig;
    }

    public static DevopsAuthenticationDirectiveFactory newInstance(
            final JwtAuthenticationFactory jwtAuthenticationFactory, final DevOpsConfig devOpsConfig,
            final Config dittoExtensionConfig) {

        final JwtAuthenticationProvider jwtAuthenticationProvider = JwtAuthenticationProvider.newInstance(
                jwtAuthenticationFactory.newJwtAuthenticationResultProvider(
                        dittoExtensionConfig, "devops"
                ),
                jwtAuthenticationFactory.getJwtValidator()
        );
        return new DevopsAuthenticationDirectiveFactory(jwtAuthenticationProvider, devOpsConfig);
    }

    /**
     * Updates the DevOps configuration. Called when dynamic config changes are detected.
     *
     * @param devOpsConfig the new DevOps config.
     */
    public void updateDevOpsConfig(final DevOpsConfig devOpsConfig) {
        this.devOpsConfig = devOpsConfig;
    }

    /**
     * Returns a lazy devops authentication directive that re-evaluates the current config on each request.
     *
     * @return the devops authentication directive.
     */
    public DevopsAuthenticationDirective status() {
        return (realm, dittoHeaders, inner) -> createStatusDirective()
                .authenticateDevOps(realm, dittoHeaders, inner);
    }

    /**
     * Returns a lazy devops authentication directive that re-evaluates the current config on each request.
     *
     * @return the devops authentication directive.
     */
    public DevopsAuthenticationDirective devops() {
        return (realm, dittoHeaders, inner) -> createDevopsDirective()
                .authenticateDevOps(realm, dittoHeaders, inner);
    }

    private DevopsAuthenticationDirective createStatusDirective() {
        final DevOpsConfig currentConfig = devOpsConfig;
        if (!currentConfig.isSecured() || !currentConfig.isStatusSecured()) {
            return DevOpsInsecureAuthenticationDirective.getInstance();
        }
        return switch (currentConfig.getStatusAuthenticationMethod()) {
            case BASIC -> DevOpsBasicAuthenticationDirective.status(currentConfig);
            case OAUTH2 -> DevOpsOAuth2AuthenticationDirective.status(currentConfig, jwtAuthenticationProvider);
        };
    }

    private DevopsAuthenticationDirective createDevopsDirective() {
        final DevOpsConfig currentConfig = devOpsConfig;
        if (!currentConfig.isSecured()) {
            return DevOpsInsecureAuthenticationDirective.getInstance();
        }
        return switch (currentConfig.getDevopsAuthenticationMethod()) {
            case BASIC -> DevOpsBasicAuthenticationDirective.devops(currentConfig);
            case OAUTH2 -> DevOpsOAuth2AuthenticationDirective.devops(currentConfig, jwtAuthenticationProvider);
        };
    }
}
