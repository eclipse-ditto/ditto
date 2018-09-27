/*
 *  Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 *  SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity.placeholder;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * The {@link EnforcementFilter} can be used to match the given input against the input which was provided to the
 * {@link EnforcementFilter} instance at creation time.
 *
 * @param <M> the type that is used to resolve the value for the match, e.g. for a {@code HeadersPlaceholder} this
 * would be a {@code Map<String, String>}.
 */
public interface EnforcementFilter<M> {

    /**
     * Matches the input (which must already be known to the {@link EnforcementFilter} against the value that is
     * resolved from the matcherSource using the configured placeholder.
     *
     * @param matcherSource the source from which the value that is matched against the input is resolved
     * @param dittoHeaders the ditto headers, required if an exception is thrown
     */
    void match(M matcherSource, final DittoHeaders dittoHeaders);
}
