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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;

/**
 * Abstract implementation of {@link Forms}.
 */
abstract class AbstractForms<E extends FormElement<E>> implements Forms<E> {

    protected final Collection<E> formElements;

    protected AbstractForms(final Collection<E> formElements) {
        this.formElements = checkNotNull(formElements, "formElements");
    }

    @Override
    public Iterator<E> iterator() {
        return formElements.iterator();
    }

    @Override
    public JsonArray toJson() {
        return formElements.stream()
                .map(FormElement::toJson)
                .collect(JsonCollectors.valuesToArray());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractForms<?> that = (AbstractForms<?>) o;
        return canEqual(that) && Objects.equals(formElements, that.formElements);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractForms;
    }

    @Override
    public int hashCode() {
        return Objects.hash(formElements);
    }

    @Override
    public String toString() {
        return "formElements=" + formElements;
    }
}
