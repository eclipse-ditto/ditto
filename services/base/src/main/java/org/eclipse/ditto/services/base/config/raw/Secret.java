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
package org.eclipse.ditto.services.base.config.raw;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * This class represents a secret that consists of a key and an associated value.
 */
@Immutable
final class Secret {

    private final String name;
    private final String value;

    private Secret(final String name, final String value) {
        this.name = checkNotNull(name, "Secret name");
        this.value = checkNotNull(value, "Secret value");
    }

    /**
     * Returns a new instance of {@code Secret} with the specified key and value.
     *
     * @param key the key of the returned secret.
     * @param value the value of the returned secret.
     * @return the new Secret object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Secret newInstance(final String key, final String value) {
        return new Secret(key, value);
    }

    /**
     * Returns the name of the secret.
     *
     * @return the name.
     */
    String getName() {
        return name;
    }

    /**
     * Returns the value of the secret.
     *
     * @return the value.
     */
    String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Secret secret = (Secret) o;
        return Objects.equals(name, secret.name) && Objects.equals(value, secret.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", name=" + name +
                ", value=" + value +
                "]";
    }

}
