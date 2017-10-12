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
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

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
