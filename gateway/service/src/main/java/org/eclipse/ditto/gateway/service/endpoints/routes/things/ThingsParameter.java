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
package org.eclipse.ditto.gateway.service.endpoints.routes.things;

/**
 * An enumeration of the query parameters for the things REST API.
 */
public enum ThingsParameter {

    /**
     * Request parameter for getting only Things with the specified IDs.
     */
    IDS("ids"),

    /**
     * Request parameter for including only the selected fields in the Thing JSON document(s).
     */
    FIELDS("fields");

    private final String parameterValue;

    ThingsParameter(final String parameterValue) {
        this.parameterValue = parameterValue;
    }

    @Override
    public String toString() {
        return parameterValue;
    }

}
