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
package org.eclipse.ditto.services.authorization.util.cache.entry;

import java.util.Optional;

import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * A pair consisting of a value and a revision.
 */
@Immutable
@AllParametersAndReturnValuesAreNonnullByDefault
final class ExistentEntry<T> implements Entry<T> {

    private final long revision;
    private final T value;

    ExistentEntry(final long revision, final T value) {
        this.revision = revision;
        this.value = value;
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public Optional<T> getValue() {
        return Optional.of(value);
    }

    @Override
    public boolean exists() {
        return true;
    }

    // TODO: equals, hashcode, toString
}
