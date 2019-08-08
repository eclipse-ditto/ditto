/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.things.id;

/**
 * Implementations of this interface are associated to a {@code Thing} identified by the value
 * returned from {@link #getThingEntityId()} ()}.
 */
public interface WithThingId {

    /**
     * Returns the identifier of the associated Thing.
     *
     * @return the identifier of the associated Thing.
     * @deprecated The thing ID is now typed. Use {@link #getThingEntityId()} instead.
     */
    @Deprecated
    default String getThingId() {
        return String.valueOf(getThingEntityId());
    }

    ThingId getThingEntityId();

}
