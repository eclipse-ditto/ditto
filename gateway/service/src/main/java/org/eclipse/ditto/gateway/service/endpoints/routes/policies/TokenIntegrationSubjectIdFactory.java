/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.policies;

import java.util.Set;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.policies.model.SubjectId;

/**
 * Creator of token integration subject IDs.
 */
public interface TokenIntegrationSubjectIdFactory {

    /**
     * Compute the token integration subject IDs from headers and JWT.
     *
     * @param dittoHeaders the Ditto headers.
     * @param jwt the JWT.
     * @return the computed subject IDs.
     * @throws org.eclipse.ditto.placeholders.UnresolvedPlaceholderException if mandatory placeholders could not
     * be resolved within the configured {@code subjectTemplate} of this TokenIntegrationSubjectIdFactory.
     */
    Set<SubjectId> getSubjectIds(DittoHeaders dittoHeaders, JsonWebToken jwt);
}
