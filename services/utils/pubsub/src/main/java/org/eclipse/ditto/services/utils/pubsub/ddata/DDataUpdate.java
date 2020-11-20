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
package org.eclipse.ditto.services.utils.pubsub.ddata;

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
}
