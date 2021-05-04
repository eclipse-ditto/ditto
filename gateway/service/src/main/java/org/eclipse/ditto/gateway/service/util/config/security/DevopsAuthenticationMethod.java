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
package org.eclipse.ditto.gateway.service.util.config.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enumerates all know authentication methods that can be used for devops and status resources.
 */
public enum DevopsAuthenticationMethod {

    OAUTH2("oauth2"),
    BASIC("basic");

    private final String methodName;

    DevopsAuthenticationMethod(final String methodName) {
        this.methodName = methodName;
    }

    String getMethodName() {
        return methodName;
    }

    public static Optional<DevopsAuthenticationMethod> fromMethodName(final String methodName) {
        return Arrays.stream(values())
                .filter(value -> value.methodName.equalsIgnoreCase(methodName))
                .findAny();
    }

}
