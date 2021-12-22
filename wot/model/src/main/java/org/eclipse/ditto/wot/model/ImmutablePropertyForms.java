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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link PropertyForms}.
 */
@Immutable
final class ImmutablePropertyForms extends AbstractForms<PropertyFormElement> implements PropertyForms {

    ImmutablePropertyForms(final Collection<PropertyFormElement> formElements) {
        super(formElements);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutablePropertyForms;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
