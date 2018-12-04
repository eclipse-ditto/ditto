/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.json;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable representation of a JSON number, which is known to fit in Java {@code double}.
 */
@Immutable
final class ImmutableJsonDouble extends AbstractJsonNumber<Double> {

    private ImmutableJsonDouble(final double value) {
        super(value);
    }

    /**
     * Returns an instance of {@code ImmutableJsonDouble}.
     *
     * @return the instance.
     */
    public static ImmutableJsonDouble of(final double value) {
        return new ImmutableJsonDouble(value);
    }

    @Override
    public boolean isDouble() {
        return true;
    }

    @Override
    public double asDouble() {
        return getValue();
    }

}
