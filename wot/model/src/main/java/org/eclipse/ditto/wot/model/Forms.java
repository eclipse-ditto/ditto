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
 * Forms is a container for {@link FormElement}s being "hypermedia controls that describe how an operation can be
 * performed".
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form</a>
 * @param <E> the type of the Form's FormElements.
 * @since 2.4.0
 */
public interface Forms<E extends FormElement<E>> extends Iterable<E>, Jsonifiable<JsonArray> {

    default Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
