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
package org.eclipse.ditto.model.base.common;

import java.util.Arrays;
import java.util.Optional;

public enum ResponseType {

    /**
     * Type of error responses.
     */
    ERROR("error"),
    /**
     * Type of negative acknowledgements responses.
     */
    N_ACK("n_ack"),
    /**
     * Type of normal responses. This includes positive acknowledgements.
     */
    RESPONSE("response");

    private final String name;

    ResponseType(final String name) {
        this.name = name;
    }

    public static Optional<ResponseType> fromName(String name) {
        final String lowerCaseName = name.toLowerCase();
        return Arrays.stream(values())
                .filter(responseType -> responseType.name.equals(lowerCaseName))
                .findAny();
    }

}
