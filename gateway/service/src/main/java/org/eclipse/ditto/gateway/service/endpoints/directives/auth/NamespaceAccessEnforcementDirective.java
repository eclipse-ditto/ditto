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
import java.util.Set;
import java.util.function.Supplier;

import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.NamespaceNotAccessibleException;
import org.eclipse.ditto.gateway.service.security.authorization.AuthorizationHeaderJwtExtractor;
import org.eclipse.ditto.gateway.service.security.authorization.NamespaceAccessValidator;
import org.eclipse.ditto.gateway.service.security.authorization.NamespaceAccessValidatorFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.jwt.model.JsonWebToken;

/**
 * Pekko HTTP directive enforcing namespace access control based on JWT claims, HTTP headers,
 * and configured namespace patterns.
 */
public final class NamespaceAccessEnforcementDirective {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(NamespaceAccessEnforcementDirective.class);

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
     * @param resourceType the resource type (e.g., "thing", "policy") for rule filtering.
     * @param inner the inner route to execute if access is allowed.
     * @return the inner route if access is allowed.
     * @throws NamespaceNotAccessibleException if the namespace is not accessible.
     */
    public Route enforceNamespaceAccessForEntityId(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final NamespacedEntityId entityId,
            final String resourceType,
            final Supplier<Route> inner) {
        return enforceNamespaceAccessForNamespace(ctx, dittoHeaders, entityId.getNamespace(), resourceType, inner);
    }

    /**
     * Validates namespace access and throws if not accessible. Extracts JWT from request context.
     *
     * @param ctx the request context containing HTTP headers.
     * @param dittoHeaders the Ditto headers.
     * @param entityId the entity ID whose namespace should be checked.
     * @param resourceType the resource type (e.g., "thing", "policy") for rule filtering.
     * @throws NamespaceNotAccessibleException if the namespace is not accessible.
     */
    public void validateNamespaceAccessForEntityId(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final NamespacedEntityId entityId,
            final String resourceType) {
        validateNamespaceAccessForNamespace(ctx, dittoHeaders, entityId.getNamespace(), resourceType);
    }

    /**
     * Enforces namespace access control for the given namespace.
     *
     * @param ctx the request context containing HTTP headers.
     * @param dittoHeaders the Ditto headers.
     * @param namespace the namespace to check.
     * @param resourceType the resource type (e.g., "thing", "policy") for rule filtering.
     * @param inner the inner route to execute if access is allowed.
     * @return the inner route if access is allowed.
     * @throws NamespaceNotAccessibleException if the namespace is not accessible.
     */
    public Route enforceNamespaceAccessForNamespace(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final String namespace,
            final String resourceType,
            final Supplier<Route> inner) {
        validateNamespaceAccessForNamespace(ctx, dittoHeaders, namespace, resourceType);
        return inner.get();
    }

    /**
     * Validates namespace access and throws if not accessible.
     *
     * @param ctx the request context containing HTTP headers.
     * @param dittoHeaders the Ditto headers.
     * @param namespace the namespace to check.
     * @param resourceType the resource type (e.g., "thing", "policy") for rule filtering.
     * @throws NamespaceNotAccessibleException if the namespace is not accessible.
     */
    public void validateNamespaceAccessForNamespace(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final String namespace,
            final String resourceType) {
        final NamespaceAccessValidator validator = createValidator(ctx, dittoHeaders, resourceType);

        if (!validator.isNamespaceAccessible(namespace)) {
            LOGGER.withCorrelationId(dittoHeaders)
                    .info("Namespace access denied for resource <{}> in namespace <{}>", resourceType, namespace);
            throw NamespaceNotAccessibleException.forNamespace(namespace, dittoHeaders);
        }
    }

    /**
     * Returns namespace patterns applicable to this request for search namespace narrowing.
     *
     * @param ctx the request context containing HTTP headers.
     * @param dittoHeaders the Ditto headers.
     * @param resourceType the resource type (e.g., "thing", "policy") for rule filtering.
     * @return applicable namespace patterns, or empty if no namespace narrowing should be applied.
     */
    public Optional<Set<String>> getApplicableNamespacePatterns(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final String resourceType) {
        return createValidator(ctx, dittoHeaders, resourceType).getApplicableNamespacePatterns();
    }

    private NamespaceAccessValidator createValidator(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String resourceType) {
        return validatorFactory.createValidator(dittoHeaders, extractJwtFromRequest(ctx).orElse(null), resourceType);
    }

    private static Optional<JsonWebToken> extractJwtFromRequest(final RequestContext ctx) {
        return ctx.getRequest()
                .getHeader("authorization")
                .map(HttpHeader::value)
                .flatMap(AuthorizationHeaderJwtExtractor::extractJwt);
    }

}
