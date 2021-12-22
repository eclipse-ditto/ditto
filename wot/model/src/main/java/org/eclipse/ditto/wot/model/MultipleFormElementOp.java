/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;

/**
 * MultipleFormElementOp is a container for multiple {@link FormElementOp}s.
 *
 * @param <O> the type of the FormElementOps.
 * @since 2.4.0
 */
public interface MultipleFormElementOp<O extends FormElementOp<O>> extends Iterable<O>,
        Jsonifiable<JsonArray> {

    default Stream<O> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
