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
    private final DevOpsConfig devOpsConfig;

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

    public DevopsAuthenticationDirective status() {
        if (!devOpsConfig.isSecured() || !devOpsConfig.isStatusSecured()) {
            return DevOpsInsecureAuthenticationDirective.getInstance();
        }
        return switch (devOpsConfig.getStatusAuthenticationMethod()) {
            case BASIC -> DevOpsBasicAuthenticationDirective.status(devOpsConfig);
            case OAUTH2 -> DevOpsOAuth2AuthenticationDirective.status(devOpsConfig, jwtAuthenticationProvider);
        };

    }

    public DevopsAuthenticationDirective devops() {
        if (!devOpsConfig.isSecured()) {
            return DevOpsInsecureAuthenticationDirective.getInstance();
        }
        return switch (devOpsConfig.getDevopsAuthenticationMethod()) {
            case BASIC -> DevOpsBasicAuthenticationDirective.devops(devOpsConfig);
            case OAUTH2 -> DevOpsOAuth2AuthenticationDirective.devops(devOpsConfig, jwtAuthenticationProvider);
        };
    }
}
