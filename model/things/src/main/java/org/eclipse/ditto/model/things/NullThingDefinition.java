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
package org.eclipse.ditto.model.things;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link ThingDefinition} which represents {@code null}.
 */
@Immutable
final class NullThingDefinition implements ThingDefinition {


    private NullThingDefinition() {
    }

    /**
     * Returns an instance of {@code NullThingDefinition}.
     *
     * @return the instance.
     */
    public static ThingDefinition getInstance() {
        return new NullThingDefinition();
    }

    @Override
    public String getNamespace() {
        return "";
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getVersion() {
        return "";
    }

    @Override
    public int length() {
        return 0;
    }

    @Override
    public char charAt(final int index) {
        return 0;
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return "";
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getClass().getName());
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        return obj != null && getClass() == obj.getClass();
    }
}
