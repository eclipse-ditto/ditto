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
 * {@link EnforcementFilter} instance at creation time. Typically this is specific to a connection type (mqtt, amqp,
 * ...) and is done where message are received. That's why this step is separated from the actual match, which is
 * usually done later in the processing chain of an incoming message.
 *
 * @param <M> the type that is used to resolve the value for the match, e.g. for a {@code HeadersPlaceholder} this
 * would be a {@code Map<String, String>}.
 */
public interface EnforcementFilter<M> {

    /**
     * Matches the input (which must already be known to the {@link EnforcementFilter}) against the values that are
     * resolved from the matcherSource using the configured placeholder. A match in this context is successful if the
     * resolved input is equal to one of the resolved matchers.     *
     *
     * @param matcherSource the source from which the the placeholders of the matchers are resolved
     * @param dittoHeaders the ditto headers, required if an exception is thrown
     * @throws org.eclipse.ditto.model.connectivity.IdEnforcementFailedException if none of the configured matchers
     * was equal to the input
     */
    void match(M matcherSource, final DittoHeaders dittoHeaders);
}
