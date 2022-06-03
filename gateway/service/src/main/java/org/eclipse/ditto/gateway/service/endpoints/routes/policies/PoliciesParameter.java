/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

/**
 * An enumeration of the query parameters for the policies REST API.
 */
public enum PoliciesParameter {

    /**
     * Request parameter for including only the selected fields in the Policy JSON document(s).
     */
    FIELDS("fields");

    private final String parameterValue;

    PoliciesParameter(final String parameterValue) {
        this.parameterValue = parameterValue;
    }

    @Override
    public String toString() {
        return parameterValue;
    }

}
