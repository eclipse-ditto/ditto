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
package org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch;

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
