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
 * Internal command to check the health of underlying systems.
 */
@Immutable
final class CheckHealth implements Serializable {

    private static final long serialVersionUID = 1L;

    private CheckHealth() {
    }

    /**
     * Returns a new {@code CheckHealth} instance.
     *
     * @return the new CheckHealth instance.
     */
    public static CheckHealth newInstance() {
        return new CheckHealth();
    }
}
