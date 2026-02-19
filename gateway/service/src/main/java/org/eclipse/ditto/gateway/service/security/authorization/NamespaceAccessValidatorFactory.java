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
package org.eclipse.ditto.gateway.service.security.authorization;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.util.config.security.NamespaceAccessConfig;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Factory for creating {@link NamespaceAccessValidator} instances with per-request context.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class NamespaceAccessValidatorFactory {

    private final List<NamespaceAccessConfig> namespaceAccessConfigs;

    public NamespaceAccessValidatorFactory(final List<NamespaceAccessConfig> namespaceAccessConfigs) {
        this.namespaceAccessConfigs = namespaceAccessConfigs;
    }

    /**
     * Creates a validator with explicit JWT.
     *
     * @param dittoHeaders the Ditto headers containing request context.
     * @param jwt the JSON Web Token, may be null.
     * @return a new namespace access validator.
     */
    public NamespaceAccessValidator createValidator(final DittoHeaders dittoHeaders,
            @Nullable final JsonWebToken jwt) {
        return new NamespaceAccessValidator(namespaceAccessConfigs, dittoHeaders, jwt);
    }

    /**
     * Creates a validator, attempting to extract JWT from the authorization header.
     *
     * @param dittoHeaders the Ditto headers containing request context.
     * @return a new namespace access validator.
     */
    public NamespaceAccessValidator createValidator(final DittoHeaders dittoHeaders) {
        final JsonWebToken jwt = extractJwtFromHeaders(dittoHeaders).orElse(null);
        return new NamespaceAccessValidator(namespaceAccessConfigs, dittoHeaders, jwt);
    }

    private static Optional<JsonWebToken> extractJwtFromHeaders(final DittoHeaders dittoHeaders) {
        return Optional.ofNullable(dittoHeaders.get(DittoHeaderDefinition.AUTHORIZATION.getKey()))
                .map(ImmutableJsonWebToken::fromAuthorization);
    }

}
