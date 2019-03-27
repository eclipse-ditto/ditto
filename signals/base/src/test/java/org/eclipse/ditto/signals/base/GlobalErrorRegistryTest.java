package org.eclipse.ditto.signals.base;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

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
public class GlobalErrorRegistryTest {

    @Test
    public void globalErrorRegistryKnowsJsonTypeNotParsableException() {
        final GlobalErrorRegistry instance = GlobalErrorRegistry.getInstance();
        assertThat(instance.getTypes()).contains(JsonTypeNotParsableException.ERROR_CODE);
    }

}
