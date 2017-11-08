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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth.dummy;

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;
import static org.eclipse.ditto.services.gateway.endpoints.utils.HttpUtils.getRequestHeader;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.AuthenticationProvider;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Implementation of {@link AuthenticationProvider} which performs Dummy authentication for development purposes.
 * <p><strong>Note: </strong>Don't use in production!</p>
 */
public final class DummyAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DummyAuthenticationProvider.class);

    /**
     * Returns the instance of this directive.
     */
    public static final DummyAuthenticationProvider INSTANCE = new DummyAuthenticationProvider();

    private DummyAuthenticationProvider() {
        //no op
    }

    @Override
    public boolean isApplicable(final RequestContext context) {
        return getRequestHeader(context, HttpHeader.X_DITTO_DUMMY_AUTH.getName()).isPresent();
    }

    @Override
    public Route unauthorized(final String correlationId) {
        return Directives.complete(StatusCodes.UNAUTHORIZED);
    }

    /**
     * Performs a dummy authentication by checking if the header
     * {@link HttpHeader#X_DITTO_DUMMY_AUTH} is set and creates an
     * {@link AuthorizationContext} from the subjects in the header value.
     * <p><strong>Note: </strong>Don't use in production!</p>
     *
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner route which will be wrapped with the {@link AuthorizationContext}
     * @return the inner route wrapped with the {@link AuthorizationContext}
     */
    @Override
    public Route authenticate(final String correlationId, final Function<AuthorizationContext, Route> inner) {
        return extractRequestContext(requestContext -> enhanceLogWithCorrelationId(correlationId, () -> {
            final String dummyAuth =
                    getRequestHeader(requestContext, HttpHeader.X_DITTO_DUMMY_AUTH.getName()) //
                            .orElseThrow(
                                    () -> new IllegalStateException("This method must not be called if header'" +
                                            HttpHeader.X_DITTO_DUMMY_AUTH.getName() + "' is not set"));

            final List<AuthorizationSubject> authorizationSubjects = extractAuthorizationSubjects(dummyAuth);
            if (authorizationSubjects.isEmpty()) {
                throw GatewayAuthenticationFailedException.newBuilder("Failed to extract AuthorizationSubjects from "
                        + HttpHeader.X_DITTO_DUMMY_AUTH.getName() +
                        " header value '" + dummyAuth + "'.").build();
            }

            final AuthorizationContext authorizationContext = AuthorizationModelFactory //
                    .newAuthContext(authorizationSubjects);


            LOGGER.warn("Dummy authentication has been applied for the following subjects: {}", dummyAuth);

            return inner.apply(authorizationContext);
        }));
    }

    private List<AuthorizationSubject> extractAuthorizationSubjects(final String subjectsCommaSeparated) {
        if (subjectsCommaSeparated != null && !subjectsCommaSeparated.isEmpty()) {
            return Arrays.stream(subjectsCommaSeparated.split(",")) //
                    .map(AuthorizationModelFactory::newAuthSubject) //
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }
}
