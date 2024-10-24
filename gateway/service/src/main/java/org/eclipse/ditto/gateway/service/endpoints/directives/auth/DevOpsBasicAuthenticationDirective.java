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

import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.server.directives.SecurityDirectives;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.util.config.security.DevOpsConfig;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;

/**
 * Custom Pekko Http directive performing basic auth for a defined {@link #USER_DEVOPS devops user}.
 */
public final class DevOpsBasicAuthenticationDirective implements DevopsAuthenticationDirective {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(DevOpsBasicAuthenticationDirective.class);

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

    @Override
    public Route authenticateDevOps(final String realm, final DittoHeaders dittoHeaders, final Route inner) {
        LOGGER.withCorrelationId(dittoHeaders).debug("DevOps basic authentication is enabled for {}.", realm);
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
