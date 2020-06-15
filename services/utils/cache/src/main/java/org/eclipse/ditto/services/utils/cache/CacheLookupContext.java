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
package org.eclipse.ditto.services.utils.cache;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Additional context provided for cache lookups using {@link EntityIdWithResourceType} as caching key.
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

}
