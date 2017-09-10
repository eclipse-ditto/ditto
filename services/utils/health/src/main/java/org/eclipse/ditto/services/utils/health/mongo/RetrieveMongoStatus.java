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
package org.eclipse.ditto.services.utils.health.mongo;

import java.io.Serializable;

import javax.annotation.concurrent.Immutable;

/**
 * Internal command to retrieve the status of underlying systems.
 */
@Immutable
public final class RetrieveMongoStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private RetrieveMongoStatus() {
    }

    /**
     * Returns a new {@code RetrieveMongoStatus} instance.
     *
     * @return the new RetrieveMongoStatus instance.
     */
    public static RetrieveMongoStatus newInstance() {
        return new RetrieveMongoStatus();
    }
}
