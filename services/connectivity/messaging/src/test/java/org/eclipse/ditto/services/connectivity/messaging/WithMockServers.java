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
package org.eclipse.ditto.services.connectivity.messaging;

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
