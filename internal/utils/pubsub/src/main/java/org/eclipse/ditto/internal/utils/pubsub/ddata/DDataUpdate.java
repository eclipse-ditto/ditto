/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.ddata;

import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Updates consisting of insertions and deletions.
 *
 * @param <S> type of insertions and deletions.
 */
@NotThreadSafe
public interface DDataUpdate<S> {

    /**
     * @return Inserted elements.
     */
    Set<S> getInserts();

    /**
     * @return Deleted elements.
     */
    Set<S> getDeletes();

    /**
     * Compute the difference between 2 DDataUpdates as another update.
     *
     * @param previousState the previous DDataUpdate.
     * @return the difference.
     */
    DDataUpdate<S> diff(DDataUpdate<S> previousState);

    /**
     * @return whether this update is empty.
     */
    boolean isEmpty();
}
