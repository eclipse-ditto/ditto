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
package org.eclipse.ditto.model.connectivity;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.type.EntityType;

/**
 * Constants to support working with connectivity.
 *
 * @since 1.1.0
 */
@Immutable
public final class ConnectivityConstants {

    private ConnectivityConstants() {
        throw new AssertionError();
    }

    /**
     * The entity type of connectivity.
     */
    public static final EntityType ENTITY_TYPE = EntityType.of("connectivity");

    /**
     * The entity type of a {@link Connection}.
     */
    public static final EntityType CONNECTION_ENTITY_TYPE = EntityType.of("connection");

}
