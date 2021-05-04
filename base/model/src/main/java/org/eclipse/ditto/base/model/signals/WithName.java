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
package org.eclipse.ditto.base.model.signals;

/**
 * Implementations of this interface are associated to an entity identified by the value returned from
 * {@link #getName()}.
 */
public interface WithName {

    /**
     * Returns the name of this entity.
     *
     * @return the name.
     */
    String getName();

}
