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
