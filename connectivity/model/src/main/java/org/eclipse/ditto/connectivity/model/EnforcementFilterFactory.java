/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import javax.annotation.Nullable;

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
    @Nullable
    EnforcementFilter<M> getFilter(I input);
}
