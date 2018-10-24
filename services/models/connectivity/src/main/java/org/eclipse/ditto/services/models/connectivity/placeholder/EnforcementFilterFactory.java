/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity.placeholder;

/**
 * Factory that creates {@link EnforcementFilter}s. As part of the creation the input value is resolved and can
 * later be matched against other values.
 *
 * @param <I> the type required to resolve the placeholders in the input
 * @param <M> the type required to resolve the placeholders in the filters
 */
public interface EnforcementFilterFactory<I, M> {

    /**
     * Creates a new {@link EnforcementFilter} which holds the resolved input value.
     *
     * @param input the source from which the input value is resolved
     * @return a new instance of an {@link EnforcementFilter}
     */
    EnforcementFilter<M> getFilter(I input);
}
