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
package org.eclipse.ditto.gateway.service.endpoints.routes.thingsearch;

/**
 * An enumeration of the query parameters for the thing-search HTTP API.
 */
public enum ThingSearchParameter {

    /**
     * Request parameter for the RQL search filter to apply.
     */
    FILTER("filter"),

    /**
     * Request parameter for the options (sort, limit) to apply.
     */
    OPTION("option"),

    /**
     * Request parameter for including only the selected fields in the Thing JSON document(s).
     */
    FIELDS("fields"),

    /**
     * Request parameter for namespaces to apply.
     */
    NAMESPACES("namespaces");

    private final String parameterValue;

    ThingSearchParameter(final String parameterValue) {
        this.parameterValue = parameterValue;
    }

    @Override
    public String toString() {
        return parameterValue;
    }

}
