/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.distributedcache.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.utils.distributedcache.actors.CacheRole.THING;

import org.junit.Test;

/**
 * Unit test for {@link CacheRole}.
 */
public final class CacheRoleTest {

    @Test
    public void testName() {
        assertThat(THING.name()).isEqualTo("THING");
    }

    @Test
    public void testToString() {
        assertThat(THING.toString()).isEqualTo("thing");
    }

}
