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
