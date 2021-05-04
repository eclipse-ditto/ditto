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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

/**
 * Used to instantiate new instances of {@link JwtAuthorizationSubjectsProvider}
 */
@FunctionalInterface
public interface JwtAuthorizationSubjectsProviderFactory {

    /**
     * Creates a new instance of {@link JwtAuthorizationSubjectsProvider}.
     *
     * @param subjectIssuersConfig the subject issuers config.
     * @return the new instance.
     */
    JwtAuthorizationSubjectsProvider newProvider(JwtSubjectIssuersConfig subjectIssuersConfig);

}
