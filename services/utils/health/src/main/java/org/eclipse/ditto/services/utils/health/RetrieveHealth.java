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
package org.eclipse.ditto.services.utils.health;

import java.io.Serializable;

import javax.annotation.concurrent.Immutable;

/**
 * Internal command to retrieve the health of underlying systems.
 */
@Immutable
public final class RetrieveHealth implements Serializable {

    private static final long serialVersionUID = 1L;

    private RetrieveHealth() {
    }

    /**
     * Returns a new {@code RetrieveHealth} instance.
     *
     * @return the new RetrieveHealth instance.
     */
    public static RetrieveHealth newInstance() {
        return new RetrieveHealth();
    }
}
