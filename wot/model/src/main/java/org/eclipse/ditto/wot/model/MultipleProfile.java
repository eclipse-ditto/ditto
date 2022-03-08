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
import java.util.stream.StreamSupport;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;

/**
 * MultipleSecurity is a container for multiple {@link SingleProfile}s.
 *
 * @since 2.4.0
 */
public interface MultipleProfile extends Profile, Iterable<SingleProfile>, Jsonifiable<JsonArray> {

    static MultipleProfile fromJson(final JsonArray jsonArray) {
        final List<SingleProfile> singleProfiles = jsonArray.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(SingleProfile::of)
                .collect(Collectors.toList());
        return of(singleProfiles);
    }

    static MultipleProfile of(final Collection<SingleProfile> profiles) {
        return new ImmutableMultipleProfile(profiles);
    }

    default Stream<SingleProfile> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
