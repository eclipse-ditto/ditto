/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import java.util.Arrays;

import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogType;
import org.junit.Test;

/**
 * Unit test for {@link ConnectionLoggerFactory}.
 */
public final class ConnectionLoggerFactoryTest {

    @Test
    @SuppressWarnings("squid:S2699")
    public void verifyAllCategoriesAndTypesRunWithoutException() {
        // make sure that developers won't forget to add new categories in the ConnectionLoggerFactory.
        // Otherwise an exception will be thrown.

        Arrays.stream(LogCategory.values())
                .forEach(category -> {
                    Arrays.stream(LogType.values())
                            .forEach(type -> ConnectionLoggerFactory.newEvictingLogger(1, 1, category, type, "1"));
                });
    }

}
