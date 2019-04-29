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
