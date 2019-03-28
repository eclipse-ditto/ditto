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
package org.eclipse.ditto.services.things.starter;

import java.util.Arrays;

import org.eclipse.ditto.services.utils.test.GlobalEventRegistryTestCases;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;

public class ThingsServiceGlobalEventRegistryTest extends GlobalEventRegistryTestCases {

    public ThingsServiceGlobalEventRegistryTest() {
        super(Arrays.asList(
                FeatureDeleted.class
        ));
    }
}
