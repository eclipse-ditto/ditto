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
package org.eclipse.ditto.connectivity.service.messaging;

import org.junit.AfterClass;

/**
 * Mixin of tests that call {@link TestConstants#getUriOfNewMockServer()} so that they also call
 * {@link TestConstants#stopMockServers()} after all tests. Note that some tests share connections; it is not safe to
 * stop mock servers after each test.
 */
public abstract class WithMockServers {

    @AfterClass
    public static void stopMockServers() {
        TestConstants.stopMockServers();
    }
}
