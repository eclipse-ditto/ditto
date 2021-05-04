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
package org.eclipse.ditto.connectivity.service.messaging.internal;

/**
 * Actor message marking that address status should be retrieved.
 */
public final class RetrieveAddressStatus {

    private static final RetrieveAddressStatus INSTANCE = new RetrieveAddressStatus();

    private RetrieveAddressStatus() {
    }

    /**
     * @return the singleton instance of this class.
     */
    public static RetrieveAddressStatus getInstance() {
        return INSTANCE;
    }
}
