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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;

/**
 * MultipleEventFormElementOp is a container for multiple {@link SingleEventFormElementOp}s.
 *
 * @since 2.4.0
 */
public interface MultipleEventFormElementOp
        extends EventFormElementOp<SingleEventFormElementOp>, MultipleFormElementOp<SingleEventFormElementOp> {

    static MultipleEventFormElementOp fromJson(final JsonArray jsonArray) {
        final List<SingleEventFormElementOp> singleEventFormOps = jsonArray.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(SingleEventFormElementOp::forName)
                .flatMap(opt -> opt.map(Stream::of).orElseGet(Stream::empty))
                .collect(Collectors.toList());
        return of(singleEventFormOps);
    }

    static MultipleEventFormElementOp of(final Collection<SingleEventFormElementOp> ops) {
        return new ImmutableMultipleEventFormElementOp(ops);
    }

}
