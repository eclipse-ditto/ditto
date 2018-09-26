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
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;

/**
 * An immutable implementation of {@link ResourceKey}.
 */
@Immutable
final class ImmutableResourceKey implements ResourceKey {

    private final String resourceType;
    private final JsonPointer resourcePath;

    private ImmutableResourceKey(final String resourceType, final JsonPointer resourcePath) {
        this.resourceType = resourceType;
        this.resourcePath = resourcePath;
    }

    /**
     * Returns a new instance of {@code ImmutableResourceKey} based on the provided {@code type} and {@code path}.
     *
     * @param type the type of the ResourceKey to create.
     * @param path the path of the ResourceKey to create.
     * @return a new ResourceKey.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code type} is empty.
     */
    public static ImmutableResourceKey newInstance(final CharSequence type, final JsonPointer path) {
        return new ImmutableResourceKey(argumentNotEmpty(type, "type").toString(), checkNotNull(path, "path"));
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    @Override
    public JsonPointer getResourcePath() {
        return resourcePath;
    }

    @Override
    public int length() {
        return toString().length();
    }

    @Override
    public char charAt(final int index) {
        return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return toString().subSequence(start, end);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableResourceKey that = (ImmutableResourceKey) o;
        return Objects.equals(resourceType, that.resourceType) && Objects.equals(resourcePath, that.resourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, resourcePath);
    }

    @Override
    @Nonnull
    public String toString() {
        return resourceType + KEY_DELIMITER + resourcePath.toString();
    }

}
