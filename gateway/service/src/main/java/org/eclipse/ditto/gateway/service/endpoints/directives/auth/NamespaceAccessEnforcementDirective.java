/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.NamespaceNotAccessibleException;
import org.eclipse.ditto.gateway.service.security.authorization.NamespaceAccessValidator;
import org.eclipse.ditto.gateway.service.security.authorization.NamespaceAccessValidatorFactory;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pekko HTTP directive enforcing namespace access control based on JWT claims, HTTP headers,
 * and configured namespace patterns.
 */
public final class NamespaceAccessEnforcementDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamespaceAccessEnforcementDirective.class);

    private final NamespaceAccessValidatorFactory validatorFactory;

    public NamespaceAccessEnforcementDirective(final NamespaceAccessValidatorFactory validatorFactory) {
        this.validatorFactory = checkNotNull(validatorFactory, "validatorFactory");
    }

    /**
     * Enforces namespace access control, extracting JWT from the request context.
     *
     * @param ctx the request context containing HTTP headers.
     * @param dittoHeaders the Ditto headers.
     * @param entityId the entity ID whose namespace should be checked.
     * @param inner the inner route to execute if access is allowed.
     * @return the inner route if access is allowed.
     * @throws NamespaceNotAccessibleException if the namespace is not accessible.
     */
    public Route enforceNamespaceAccessForEntityId(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final NamespacedEntityId entityId,
            final Supplier<Route> inner) {
        validateNamespaceAccessForEntityId(ctx, dittoHeaders, entityId);
        return inner.get();
    }

    /**
     * Validates namespace access and throws if not accessible. Extracts JWT from request context.
     *
     * @param ctx the request context containing HTTP headers.
     * @param dittoHeaders the Ditto headers.
     * @param entityId the entity ID whose namespace should be checked.
     * @throws NamespaceNotAccessibleException if the namespace is not accessible.
     */
    public void validateNamespaceAccessForEntityId(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final NamespacedEntityId entityId) {
        final JsonWebToken jwt = extractJwtFromRequest(ctx).orElse(null);
        final NamespaceAccessValidator validator = validatorFactory.createValidator(dittoHeaders, jwt);
        final String namespace = entityId.getNamespace();

        if (!validator.isNamespaceAccessible(namespace)) {
            LOGGER.debug("Namespace access denied for: {}", namespace);
            throw NamespaceNotAccessibleException.forNamespace(namespace, dittoHeaders);
        }
    }

    @Nullable
    private static Optional<JsonWebToken> extractJwtFromRequest(final RequestContext ctx) {
        return ctx.getRequest()
                .getHeader("authorization")
                .map(HttpHeader::value)
                .filter(value -> value.toLowerCase().startsWith("bearer "))
                .map(ImmutableJsonWebToken::fromAuthorization);
    }

}
