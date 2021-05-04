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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.gateway.service.util.config.security.DevOpsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.directives.SecurityDirectives;

/**
 * Custom Akka Http directive performing basic auth for a defined {@link #USER_DEVOPS devops user}.
 */
public final class DevOpsBasicAuthenticationDirective implements DevopsAuthenticationDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevOpsBasicAuthenticationDirective.class);

    private static final String USER_DEVOPS = "devops";

    private final Collection<String> passwords;

    private DevOpsBasicAuthenticationDirective(final String... passwords) {
        this.passwords = checkNotEmpty(Arrays.asList(checkNotNull(passwords, "passwords")), "passwords");
    }

    /**
     * Returns an instance of {@code DevOpsAuthenticationDirective} for devops resources.
     *
     * @param devOpsConfig the devops authentication config.
     * @return the instance.
     * @throws NullPointerException if {@code devOpsConfig} is {@code null}.
     */
    public static DevOpsBasicAuthenticationDirective devops(final DevOpsConfig devOpsConfig) {
        return new DevOpsBasicAuthenticationDirective(devOpsConfig.getPassword());
    }

    /**
     * Returns an instance of {@code DevOpsAuthenticationDirective} for status resources.
     *
     * @param devOpsConfig the devops authentication config.
     * @return the instance.
     * @throws NullPointerException if {@code devOpsConfig} is {@code null}.
     */
    public static DevOpsBasicAuthenticationDirective status(final DevOpsConfig devOpsConfig) {
        return new DevOpsBasicAuthenticationDirective(devOpsConfig.getPassword(), devOpsConfig.getStatusPassword());
    }

    /**
     * Authenticates the devops resources with the chosen authentication method.
     *
     * @param realm the realm to apply.
     * @param inner the inner route, which will be performed on successful authentication.
     * @return the inner route wrapped with authentication.
     */
    public Route authenticateDevOps(final String realm, final Route inner) {
        LOGGER.debug("DevOps basic authentication is enabled for {}.", realm);
        return Directives.authenticateBasic(realm, new BasicAuthenticator(passwords), userName -> inner);
    }

    private static final class BasicAuthenticator
            implements Function<Optional<SecurityDirectives.ProvidedCredentials>, Optional<String>> {

        private final Collection<String> passwords;

        BasicAuthenticator(final Collection<String> passwords) {
            this.passwords = passwords;
        }

        @Override
        public Optional<String> apply(final Optional<SecurityDirectives.ProvidedCredentials> credentials) {
            return credentials
                    .filter(providedCredentials -> USER_DEVOPS.equals(providedCredentials.identifier()))
                    .filter(providedCredentials -> passwords.stream().anyMatch(providedCredentials::verify))
                    .map(SecurityDirectives.ProvidedCredentials::identifier);
        }

    }

}
