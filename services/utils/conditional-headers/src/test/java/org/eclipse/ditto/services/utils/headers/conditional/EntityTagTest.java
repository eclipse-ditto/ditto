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

package org.eclipse.ditto.services.utils.headers.conditional;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class EntityTagTest {

    @Test
    public void weakEntityTag() {
        final EntityTag entityTag = EntityTag.fromString("W/\"hallo\"");
        assertThat(entityTag.isWeak()).isTrue();
        assertThat(entityTag.getOpaqueTag()).isEqualTo("\"hallo\"");
    }

    @Test
    public void strongEntityTag() {
        final EntityTag entityTag = EntityTag.fromString("\"hallo\"");
        assertThat(entityTag.isWeak()).isFalse();
        assertThat(entityTag.getOpaqueTag()).isEqualTo("\"hallo\"");
    }

    @Test(expected = OpaqueTagInvalidException.class)
    public void weakPrefixIsCaseSensitive() {
        EntityTag.fromString("w/\"hallo\"");
    }

    @Test(expected = OpaqueTagInvalidException.class)
    public void weakEntityTagOpaqueTagMustStartWithDoubleQuotes() {
        EntityTag.fromString("W/hallo\"");
    }

    @Test(expected = OpaqueTagInvalidException.class)
    public void strongEntityTagOpaqueTagMustStartWithDoubleQuotes() {
        EntityTag.fromString("hallo\"");
    }

    @Test(expected = OpaqueTagInvalidException.class)
    public void weakEntityTagOpaqueTagMustEndWithDoubleQuotes() {
        EntityTag.fromString("W/\"hallo");
    }

    @Test(expected = OpaqueTagInvalidException.class)
    public void strongEntityTagOpaqueTagMustEndWithDoubleQuotes() {
        EntityTag.fromString("\"hallo");
    }

    @Test(expected = OpaqueTagInvalidException.class)
    public void weakEntityTagOpaqueTagMustNotContainMoreThanTwoDoubleQuotes() {
        EntityTag.fromString("W/\"hal\"l\"o\"");
    }

    @Test(expected = OpaqueTagInvalidException.class)
    public void strongEntityTagOpaqueTagMustNotContainMoreThanTwoDoubleQuotes() {
        EntityTag.fromString("\"hal\"l\"o\"");
    }
}