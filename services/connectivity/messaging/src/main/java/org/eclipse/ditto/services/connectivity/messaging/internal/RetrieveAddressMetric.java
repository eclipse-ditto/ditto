/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.internal;

/**
 * Actor message marking that address metrics should be retrieved.
 */
public final class RetrieveAddressMetric {

    private static final RetrieveAddressMetric INSTANCE = new RetrieveAddressMetric();

    private RetrieveAddressMetric() {
    }

    /**
     * @return the singleton instance of this class.
     */
    public static RetrieveAddressMetric getInstance() {
        return INSTANCE;
    }
}
