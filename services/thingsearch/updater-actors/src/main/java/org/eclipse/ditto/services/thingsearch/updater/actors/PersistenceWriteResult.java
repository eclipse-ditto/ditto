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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Holds the result of the write operation.
 */
final class PersistenceWriteResult {

    private final boolean success;
    private final Throwable error;

    /**
     * Constructor.
     *
     * @param success if the write was successful
     * @param error a potential error
     */
    PersistenceWriteResult(final boolean success, final Throwable error) {
        this.success = success;
        this.error = error;
    }

    boolean isSuccess() {
        return success;
    }

    /**
     * Returns the error if the write which caused this result was not successful.
     *
     * @return the error or {@code null}.
     */
    @Nullable
    Throwable getError() {
        return error;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PersistenceWriteResult)) {
            return false;
        }
        final PersistenceWriteResult that = (PersistenceWriteResult) o;
        return success == that.success && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, error);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "success=" + success + ", error=" + error + "]";
    }

}
