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

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;

/**
 * PropertyForms is a container for {@link PropertyFormElement}s being
 * "hypermedia controls that describe how an operation can be performed" defined in {@link Property}s.
 *
 * @since 2.4.0
 */
public interface PropertyForms extends Forms<PropertyFormElement> {

    static PropertyForms fromJson(final JsonArray jsonArray) {
        final List<PropertyFormElement> propertyFormElements = jsonArray.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(PropertyFormElement::fromJson)
                .collect(Collectors.toList());
        return of(propertyFormElements);
    }

    static PropertyForms of(final Collection<PropertyFormElement> formElements) {
        return new ImmutablePropertyForms(formElements);
    }
}
