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
package org.eclipse.ditto.services.utils.distributedcache.actors;

/**
 * This interface represents a wrapper for either a {@link akka.cluster.ddata.Replicator.ReadConsistency} or a
 * {@link akka.cluster.ddata.Replicator.WriteConsistency} which also acts a CharSequence.
 *
 * @param <T> the type of the wrapped replicator consistency.
 */
public interface Consistency<T> extends CharSequence {

    @Override
    default int length() {
        return toString().length();
    }

    @Override
    default char charAt(final int index) {
        return toString().charAt(index);
    }

    @Override
    default CharSequence subSequence(final int start, final int end) {
        return toString().subSequence(start, end);
    }
    /**
     * Returns the appropriate replicator consistency which is wrapped by this Consistency.
     *
     * @return the consistency.
     */
    T getReplicatorConsistency();

}
