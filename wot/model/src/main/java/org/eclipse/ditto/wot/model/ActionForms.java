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
 * ActionForms is a container for {@link ActionFormElement}s being
 * "hypermedia controls that describe how an operation can be performed" defined in {@link Action}s.
 *
 * @since 2.4.0
 */
public interface ActionForms extends Forms<ActionFormElement> {

    /**
     * Creates ActionForms from the specified JSON array.
     *
     * @param jsonArray the JSON array of form element objects.
     * @return the ActionForms.
     */
    static ActionForms fromJson(final JsonArray jsonArray) {
        final List<ActionFormElement> actionFormElements = jsonArray.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ActionFormElement::fromJson)
                .collect(Collectors.toList());

        return of(actionFormElements);
    }

    /**
     * Creates ActionForms from the specified collection of form elements.
     *
     * @param formElements the collection of form elements.
     * @return the ActionForms.
     */
    static ActionForms of(final Collection<ActionFormElement> formElements) {
        return new ImmutableActionForms(formElements);
    }
}
