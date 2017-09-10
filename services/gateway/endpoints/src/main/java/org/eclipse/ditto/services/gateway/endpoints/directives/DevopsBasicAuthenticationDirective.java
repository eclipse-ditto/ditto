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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.directives.SecurityDirectives;

/**
 * Custom Akka Http directive performing basic auth for 2 realms, {@value #REALM_DEVOPS} and {@value #REALM_HEALTH}.
 */
public class DevopsBasicAuthenticationDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevopsBasicAuthenticationDirective.class);

    /**
     * The Http basic auth realm for the "ditto-devops" user used for /status resource.
     */
    public static final String REALM_DEVOPS = "DITTO-DEVOPS";

    /**
     * The Http basic auth realm for the "health-user" user used for /health resource.
     */
    public static final String REALM_HEALTH = "HEALTH-USER";

    private static final String USER_DEVOPS = "devops";
    private static final String USER_HEALTH = "health-user";

    private DevopsBasicAuthenticationDirective() {
        // no op
    }

    /**
     * Authenticates with the Basic Authentication.
     *
     * @param realm the realm to apply
     * @param inner the inner route, which will be performed on successful authentication
     * @return the inner route wrapped with authentication
     */
    public static Route authenticateDevopsBasic(final String realm, final Route inner) {
        return Directives.extractActorSystem(actorSystem -> {
            final Config config = actorSystem.settings().config();
            switch (realm) {
                case REALM_DEVOPS:
                    final boolean devopsSecureStatus = config.getBoolean(ConfigKeys.DEVOPS_SECURE_STATUS);
                    if (!devopsSecureStatus) {
                        LOGGER.warn("DevOps resource is not secured by BasicAuth");
                        return inner;
                    }
                    final String devOpsPassword = config.getString(ConfigKeys.SECRETS_DEVOPS_PASSWORD);
                    LOGGER.debug("Devops authentication is enabled.");
                    return Directives.authenticateBasic(REALM_DEVOPS, new Authenticator(USER_DEVOPS, devOpsPassword),
                            (userName) -> inner);
                case REALM_HEALTH:
                    final boolean healthSecure = config.getBoolean(ConfigKeys.PUBLIC_HEALTH_SECURE);
                    if (!healthSecure) {
                        LOGGER.warn("/health resource is not secured by BasicAuth");
                        return inner;
                    }
                    final String healthPassword = config.getString(ConfigKeys.SECRETS_PUBLIC_HEALTH_PASSWORD);
                    LOGGER.debug("Devops authentication is enabled.");
                    return Directives.authenticateBasic(REALM_HEALTH, new Authenticator(USER_HEALTH, healthPassword),
                            (userName) -> inner);
                default:
                    LOGGER.warn("Did not know realm '{}'. NOT letting the inner Route pass ..", realm);
                    return Directives.complete(StatusCodes.UNAUTHORIZED);
            }
        });
    }

    private static class Authenticator
            implements Function<Optional<SecurityDirectives.ProvidedCredentials>, Optional<String>> {

        private final String username;
        private final String password;

        Authenticator(final String username, final String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public Optional<String> apply(final Optional<SecurityDirectives.ProvidedCredentials> credentials) {
            return credentials
                    .filter(providedCredentials -> username.equals(providedCredentials.identifier()) &&
                            providedCredentials.verify(password))
                    .map(SecurityDirectives.ProvidedCredentials::identifier);
        }
    }
}
