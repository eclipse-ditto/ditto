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
package org.eclipse.ditto.thingsearch.service.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Tests {@link KeyEscapeUtil}.
 */
public class KeyEscapeUtilTest {


    @Test
    public void escape() {
        assertSame("escapingNotNecessary", KeyEscapeUtil.escape("escapingNotNecessary"));
        assertEquals("~1org~2eclipse~2~0ditto", KeyEscapeUtil.escape("$org.eclipse.~ditto"));
    }
}
