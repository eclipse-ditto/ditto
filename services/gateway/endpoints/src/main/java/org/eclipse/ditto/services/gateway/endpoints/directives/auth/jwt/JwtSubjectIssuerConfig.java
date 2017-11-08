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

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import org.eclipse.ditto.model.policies.SubjectIssuer;

/**
 * Configuration for a UNKNOWN issuer.
 */
public final class JwtSubjectIssuerConfig {
    private final SubjectIssuer subjectIssuer;
    private final String jwkResource;

    /**
     * Constructor.
     *
     * @param subjectIssuer the UNKNOWN issuer
     * @param jwkResource the JWK resource URL
     */
    public JwtSubjectIssuerConfig(final SubjectIssuer subjectIssuer, final String jwkResource) {
        this.subjectIssuer = requireNonNull(subjectIssuer);
        this.jwkResource = requireNonNull(jwkResource);
    }

    /**
     * Returns the UNKNOWN issuer.
     *
     * @return the UNKNOWN issuer
     */
    public SubjectIssuer getSubjectIssuer() {
        return subjectIssuer;
    }

    /**
     * Returns the JWK resource URL.
     *
     * @return the JWK resource URL
     */
    public String getJwkResource() {
        return jwkResource;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JwtSubjectIssuerConfig that = (JwtSubjectIssuerConfig) o;
        return Objects.equals(subjectIssuer, that.subjectIssuer) &&
                Objects.equals(jwkResource, that.jwkResource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectIssuer, jwkResource);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [subjectIssuer=" + subjectIssuer +
                ", jwkResource='" + jwkResource + '\'' +
                ']';
    }
}
