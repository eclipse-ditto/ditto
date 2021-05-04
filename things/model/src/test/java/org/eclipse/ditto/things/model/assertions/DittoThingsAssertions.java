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
package org.eclipse.ditto.things.model.assertions;

import org.eclipse.ditto.base.model.assertions.DittoBaseAssertions;
import org.eclipse.ditto.things.model.Thing;

/**
 * Project specific {@link org.assertj.core.api.Assertions} to extends the set of assertions which are provided by FEST.
 */
public class DittoThingsAssertions extends DittoBaseAssertions {

    /**
     * Returns an assert for the given {@link org.eclipse.ditto.things.model.Thing}.
     *
     * @param thing the Thing to be checked.
     * @return the assert for {@code thing}.
     */
    public static ThingAssert assertThat(final Thing thing) {
        return new ThingAssert(thing);
    }

}
