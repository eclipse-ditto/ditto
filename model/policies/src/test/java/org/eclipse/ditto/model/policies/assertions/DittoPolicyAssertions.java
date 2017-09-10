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
package org.eclipse.ditto.model.policies.assertions;

import org.eclipse.ditto.model.base.assertions.DittoBaseAssertions;
import org.eclipse.ditto.model.policies.Policy;

/**
 * Project specific {@link org.assertj.core.api.Assertions} to extends the set of assertions which are provided by FEST.
 */
public class DittoPolicyAssertions extends DittoBaseAssertions {

    /**
     * Returns an assert for the given {@link Policy}.
     *
     * @param policy the Policy to be checked.
     * @return the assert for {@code policy}.
     */
    public static PolicyAssert assertThat(final Policy policy) {
        return new PolicyAssert(policy);
    }

}
