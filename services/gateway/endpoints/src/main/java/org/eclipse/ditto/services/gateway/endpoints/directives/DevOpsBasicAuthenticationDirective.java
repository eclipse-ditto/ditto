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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.services.gateway.endpoints.config.DevOpsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.directives.SecurityDirectives;

/**
 * Custom Akka Http directive performing basic auth for {@value #REALM_DEVOPS} realm.
 */
public final class DevOpsBasicAuthenticationDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevOpsBasicAuthenticationDirective.class);

    /**
     * The Http basic auth realm for the "ditto-devops" user used for /status resource.
     */
    public static final String REALM_DEVOPS = "DITTO-DEVOPS";

    private static final String USER_DEVOPS = "devops";

    private final DevOpsConfig devOpsConfig;

    private DevOpsBasicAuthenticationDirective(final DevOpsConfig devOpsConfig) {
        this.devOpsConfig = checkNotNull(devOpsConfig, "DevOpsConfig");
    }

    /**
     * Returns an instance of {@code DevOpsBasicAuthenticationDirective}.
     *
     * @param devOpsConfig the configuration settings of the Gateway service's DevOps endpoint.
     * @return the instance.
     * @throws NullPointerException if {@code devOpsConfig} is {@code null}.
     */
    public static DevOpsBasicAuthenticationDirective getInstance(final DevOpsConfig devOpsConfig) {
        return new DevOpsBasicAuthenticationDirective(devOpsConfig);
    }

    /**
     * Authenticates with the Basic Authentication.
     *
     * @param realm the realm to apply.
     * @param inner the inner route, which will be performed on successful authentication.
     * @return the inner route wrapped with authentication.
     */
    public Route authenticateDevOpsBasic(final String realm, final Route inner) {
        return Directives.extractActorSystem(actorSystem -> {
            if (REALM_DEVOPS.equals(realm)) {
                if (!devOpsConfig.isSecureStatus()) {
                    LOGGER.warn("DevOps resource is not secured by BasicAuth!");
                    return inner;
                }
                final String devOpsPassword = devOpsConfig.getPassword();
                LOGGER.debug("DevOps authentication is enabled.");
                return Directives.authenticateBasic(REALM_DEVOPS, new Authenticator(USER_DEVOPS, devOpsPassword),
                        userName -> inner);
            }
            LOGGER.warn("Did not know realm <{}>. NOT letting the inner route pass ...", realm);
            return Directives.complete(StatusCodes.UNAUTHORIZED);
        });
    }

    private static final class Authenticator
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
