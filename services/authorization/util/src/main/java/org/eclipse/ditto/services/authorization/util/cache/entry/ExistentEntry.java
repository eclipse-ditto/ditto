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

import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * A pair consisting of a value and a revision.
 */
@Immutable
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
    public T getValue() {
        return value;
    }

    @Override
    public boolean exists() {
        return true;
    }

    // TODO: equals, hashcode, toString
}
