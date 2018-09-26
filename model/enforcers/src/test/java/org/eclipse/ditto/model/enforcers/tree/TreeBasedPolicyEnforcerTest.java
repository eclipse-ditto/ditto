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
package org.eclipse.ditto.model.enforcers.tree;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.Test;

/**
 * Unit test for {@link TreeBasedPolicyEnforcer}.
 *
 * <em>The actual unit tests are located in TreeBasedPolicyAlgorithmTest of module policies-service.</em>
 */
public final class TreeBasedPolicyEnforcerTest {

    /** */
    @Test
    public void tryToCreateInstanceWithNullPolicy() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> TreeBasedPolicyEnforcer.createInstance(null))
                .withMessage("The %s must not be null!", "policy")
                .withNoCause();
    }

}
