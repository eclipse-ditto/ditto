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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.gateway.security.jwt.JsonWebToken;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayJwtIssuerNotSupportedException;

/**
 * Implementation of
 * {@link org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.AuthorizationSubjectsProvider} for Google
 * JWTs.
 */
@Immutable
public final class DittoAuthorizationSubjectsProvider implements AuthorizationSubjectsProvider {

    private final JwtSubjectIssuersConfig jwtSubjectIssuersConfig;

    private DittoAuthorizationSubjectsProvider(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig) {
        this.jwtSubjectIssuersConfig = jwtSubjectIssuersConfig;
    }

    /**
     * Returns a new {@code DittoAuthorizationSubjectsProvider}.
     *
     * @param jwtSubjectIssuersConfig the subject issuer configuration.
     * @return the DittoAuthorizationSubjectsProvider.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DittoAuthorizationSubjectsProvider of(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig) {
        checkNotNull(jwtSubjectIssuersConfig);
        return new DittoAuthorizationSubjectsProvider(jwtSubjectIssuersConfig);
    }

    @Override
    public List<AuthorizationSubject> getAuthorizationSubjects(final JsonWebToken jsonWebToken) {
        checkNotNull(jsonWebToken);

        final String issuer = jsonWebToken.getIssuer();
        final JwtSubjectIssuerConfig jwtSubjectIssuerConfig = jwtSubjectIssuersConfig.getConfigItem(issuer)
                .orElseThrow(() -> GatewayJwtIssuerNotSupportedException.newBuilder(issuer).build());

        return jsonWebToken.getSubjects().stream()
                .map(subject -> SubjectId.newInstance(jwtSubjectIssuerConfig.getSubjectIssuer(), subject))
                .map(AuthorizationSubject::newInstance)
                .collect(Collectors.toList());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        final DittoAuthorizationSubjectsProvider that = (DittoAuthorizationSubjectsProvider) o;
        return Objects.equals(jwtSubjectIssuersConfig, that.jwtSubjectIssuersConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jwtSubjectIssuersConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "jwtSubjectIssuersConfig=" + jwtSubjectIssuersConfig +
                "]";
    }

}
