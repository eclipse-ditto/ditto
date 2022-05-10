/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.streaming.signals;

/**
 * Simple event which signals that a sent JWT was invalid.
 */
public final class InvalidJwt {

    private InvalidJwt() {
        super();
    }

    /**
     * Returns a new instance of {@code InvalidJwt}.
     *
     * @return the instance.
     * @throws NullPointerException if {@code connectionCorrelationId} is {@code null}.
     */
    public static InvalidJwt getInstance() {
        return new InvalidJwt();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
