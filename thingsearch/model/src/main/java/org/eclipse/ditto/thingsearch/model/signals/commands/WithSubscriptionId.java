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
package org.eclipse.ditto.thingsearch.model.signals.commands;

/**
 * Interface of search commands addressing a particular session identified by a subscription ID.
 *
 * @since 1.5.0
 */
public interface WithSubscriptionId<T extends ThingSearchCommand<T>> extends ThingSearchCommand<T> {

    /**
     * Returns the subscriptionId identifying the session of this search command.
     *
     * @return the subscriptionId.
     */
    String getSubscriptionId();

}
