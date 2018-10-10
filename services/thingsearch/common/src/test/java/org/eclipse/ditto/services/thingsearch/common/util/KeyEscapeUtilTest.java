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
package org.eclipse.ditto.services.thingsearch.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Tests {@link KeyEscapeUtil}.
 */
public class KeyEscapeUtilTest {

    /** */
    @Test
    public void escape() {
        assertSame("escapingNotNecessary", KeyEscapeUtil.escape("escapingNotNecessary"));
        assertEquals("\uFF04org\uFF0Eeclipse\uFF0Editto", KeyEscapeUtil.escape("$org.eclipse.ditto"));
    }
}
