package org.eclipse.ditto.signals.base;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

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
public class GlobalErrorRegistryTest {

    @Test
    public void globalErrorRegistryKnowsJsonTypeNotParsableException() {
        final GlobalErrorRegistry instance = GlobalErrorRegistry.getInstance();
        assertThat(instance.getTypes()).contains(JsonTypeNotParsableException.ERROR_CODE);
    }

}