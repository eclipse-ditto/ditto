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
package org.eclipse.ditto.placeholders.filter;

import java.util.Objects;

/**
 * Keeps the value if both passed parameters are equal to each other.
 */
final class EqFunction implements FilterFunction {

    @Override
    public String getName() {
        return "eq";
    }

    @Override
    public boolean apply(final String... parameters) {
        if (parameters.length != 2) {
            return false;
        }
        return Objects.equals(parameters[0], parameters[1]);
    }

}
