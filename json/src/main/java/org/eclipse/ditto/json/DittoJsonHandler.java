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
package org.eclipse.ditto.json;

import com.eclipsesource.json.JsonHandler;

/**
 * Common base implementation for parsing JSON string representations to ditto-json types.
 *
 * @param <A> the type to be used for parsing JSON arrays.
 * @param <O> the type to be used for parsing JSON objects.
 * @param <V> the type of the value this handler returns.
 */
abstract class DittoJsonHandler<A, O, V> extends JsonHandler<A, O> {

    /**
     * Returns the value of this handler or {@code null}.
     *
     * @return the value.
     */
    protected abstract V getValue();

}
