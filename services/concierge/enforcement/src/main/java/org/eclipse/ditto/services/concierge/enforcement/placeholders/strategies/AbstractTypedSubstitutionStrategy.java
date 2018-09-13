/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

/**
 * Abstract base class for instances of {@link SubstitutionStrategy} which match on a concrete subtype of
 * {@link WithDittoHeaders}.
 * @param <T> the subtype of {@link WithDittoHeaders} handled by this strategy.
 */
abstract class AbstractTypedSubstitutionStrategy<T extends WithDittoHeaders> implements SubstitutionStrategy<T> {
    private final Class<T> type;

    AbstractTypedSubstitutionStrategy(final Class<T> type) {
        this.type = requireNonNull(type);
    }

    @Override
    public boolean matches(final WithDittoHeaders withDittoHeaders) {
        return type.isAssignableFrom(withDittoHeaders.getClass());
    }

}
