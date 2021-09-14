/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cache;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFieldSelector;

/**
 * Additional context provided for cache lookups using {@link CacheKey} as caching key.
 */
@Immutable
public interface CacheLookupContext {

    /**
     * Returns the optional DittoHeaders this context provides.
     *
     * @return the optional DittoHeaders.
     */
    Optional<DittoHeaders> getDittoHeaders();

    /**
     * Returns the optional JsonFieldSelector this context provides.
     *
     * @return the optional JsonFieldSelector.
     */
    Optional<JsonFieldSelector> getJsonFieldSelector();

    /**
     * @return The persistence lifecycle of the entity if known.
     */
    Optional<PersistenceLifecycle> getPersistenceLifecycle();

}
