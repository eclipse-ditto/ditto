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
package org.eclipse.ditto.policies.enforcement.placeholders.strategies;

import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;

/**
 * Registry interface for determining {@link SubstitutionStrategy}s to use for a Signal.
 */
@Immutable
public interface SubstitutionStrategyRegistry {

    /**
     * Get a matching strategy for handling the given {@code withDittoHeaders}.
     *
     * @param withDittoHeaders the instance of {@link WithDittoHeaders} to be handled.
     * @return an {@link Optional} containing the first strategy which matches; an empty {@link Optional} in case no
     * strategy matches.
     */
    Optional<SubstitutionStrategy> getMatchingStrategy(DittoHeadersSettable<?> withDittoHeaders);

    /**
     * TODO TJ javadoc
     * @return
     */
    List<SubstitutionStrategy<?>> getStrategies();

}
