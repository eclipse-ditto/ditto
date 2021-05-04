/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;

/**
 * Represents a signal type that is able to return its ConnectionId. Provides a default implementation for
 * {@link #getEntityId()} which returns the ConnectionId as well.
 */
public interface WithConnectionId extends WithEntityId {

    @Override
    ConnectionId getEntityId();

}
