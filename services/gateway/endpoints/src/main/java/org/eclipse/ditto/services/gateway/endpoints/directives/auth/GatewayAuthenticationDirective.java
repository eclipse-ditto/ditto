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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth;

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.Route;

/**
 * Akka Http directive which performs authentication for the Things service.
 */
public final class GatewayAuthenticationDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAuthenticationDirective.class);

    private final List<AuthenticationProvider> authenticationChain;
    private final Function<String, Route> unauthorizedDirective;

    /**
     * Constructor.
     *
     * @param authenticationChain a list of {@link AuthenticationProvider}s (which are tried to be applied in this
     * order).
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code authenticationChain} is empty
     */
    public GatewayAuthenticationDirective(final List<AuthenticationProvider> authenticationChain) {
        this(authenticationChain, new UnauthorizedDirective());
    }

    /**
     * Constructor.
     *
     * @param authenticationChain a list of {@link AuthenticationProvider}s (which are tried to be applied in this
     * order).
     * @param unauthorizedDirective a directive providing a route for the case that a request cannot be handled by
     * any of the {@link AuthenticationProvider}s from {@code authenticationChain}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code authenticationChain} is empty
     */
    public GatewayAuthenticationDirective(final List<AuthenticationProvider> authenticationChain,
            final Function<String, Route> unauthorizedDirective) {
        checkNotNull(authenticationChain, "authenticationChain");
        argumentNotEmpty(authenticationChain, "authenticationChain");
        checkNotNull(unauthorizedDirective, "unauthorizedDirective");

        this.authenticationChain = Collections.unmodifiableList(new ArrayList<>(authenticationChain));
        this.unauthorizedDirective = unauthorizedDirective;
    }

    /**
     * Depending on the request headers, one of the supported authentication mechanisms is applied.
     *
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner route which will be wrapped with the {@link AuthorizationContext}
     * @return the inner route wrapped with the {@link AuthorizationContext}
     */
    public Route authenticate(final String correlationId, final Function<AuthorizationContext, Route> inner) {
        return extractRequestContext(
                requestContext -> DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId, () -> {
                    final Optional<AuthenticationProvider> applicableDirective = authenticationChain.stream()
                            .filter(authenticationDirective -> authenticationDirective.isApplicable(requestContext))
                            .findFirst();

                    final Uri requestUri = requestContext.getRequest().getUri();
                    if (applicableDirective.isPresent()) {
                        LOGGER.debug("Applying Authentication Directive '{}' to URI '{}'",
                                applicableDirective.getClass().getSimpleName(), requestUri);
                        return applicableDirective.get().authenticate(correlationId, inner);
                    } else {
                        LOGGER.debug("Missing Authentication for URI '{}'. Applying unauthorizedDirective '{}'",
                                requestUri, unauthorizedDirective);
                        return unauthorizedDirective.apply(correlationId);
                    }
                }));
    }

}
