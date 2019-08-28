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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.policies.SubjectIssuer;

/**
 * Configuration for a {@link org.eclipse.ditto.model.policies.SubjectIssuer}.
 */
@Immutable
public final class JwtSubjectIssuerConfig {

    private final String issuer;
    private final SubjectIssuer subjectIssuer;

    /**
     * Constructs a new {@code JwtSubjectIssuerConfig}.
     *
     * @param issuer the issuer.
     * @param subjectIssuer the subject issuer.
     */
    public JwtSubjectIssuerConfig(final String issuer, final SubjectIssuer subjectIssuer) {
        this.issuer = requireNonNull(issuer);
        this.subjectIssuer = requireNonNull(subjectIssuer);
    }

    /**
     * Returns the issuer.
     *
     * @return the issuer.
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * Returns the subject issuer.
     *
     * @return the subject issuer
     */
    public SubjectIssuer getSubjectIssuer() {
        return subjectIssuer;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final JwtSubjectIssuerConfig that = (JwtSubjectIssuerConfig) o;
        return Objects.equals(issuer, that.issuer) &&
                Objects.equals(subjectIssuer, that.subjectIssuer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, subjectIssuer);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", issuer=" + issuer +
                ", subjectIssuer=" + subjectIssuer +
                "]";
    }

}
