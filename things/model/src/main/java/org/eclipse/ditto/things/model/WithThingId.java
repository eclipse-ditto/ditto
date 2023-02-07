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
package org.eclipse.ditto.things.model;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;

/**
 * Implementations of this interface are associated to a {@code Thing} identified by the value
 * returned from {@link #getEntityId()}.
 */
public interface WithThingId extends WithEntityId {

    @Override
    ThingId getEntityId();

}
