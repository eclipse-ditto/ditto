/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.placeholders;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * The {@link EnforcementFilter} can be used to match the given input against the input which was provided to the
 * {@link EnforcementFilter} instance at creation time. Typically this is specific to a connection type (mqtt, amqp,
 * ...) and is done where message are received. That's why this step is separated from the actual match, which is
 * usually done later, after mapping the message to ditto message, in the processing chain of an incoming message.
 *
 * @param <M> the type that is used to resolve the value for the match, e.g. for a {@code HeadersPlaceholder} this
 * would be a {@code Map<String, String>}.
 */
public interface EnforcementFilter<M> {

    /**
     * Matches the input (which must already be known to the {@link EnforcementFilter}) against the values that are
     * resolved from the filterInput using the configured placeholder. A match in this context is successful if the
     * resolved input is equal to one of the resolved filters.
     *
     * @param filterInput the source from which the the placeholders in the filters are resolved
     * @param dittoHeaders the ditto headers, required if an exception is thrown
     * @throws org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException if none of the
     * configured filters was equal to the input
     */
    void match(M filterInput, DittoHeaders dittoHeaders);
}
