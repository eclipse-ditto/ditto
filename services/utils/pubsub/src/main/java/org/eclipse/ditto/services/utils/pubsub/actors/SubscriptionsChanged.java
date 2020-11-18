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
package org.eclipse.ditto.services.utils.pubsub.actors;

import org.eclipse.ditto.services.utils.pubsub.ddata.SubscriptionsReader;

/**
 * Local message sent to whomever is interested in changes to subscriptions, containing a snapshot of the current local
 * subscriptions.
 */
public final class SubscriptionsChanged {

    private final SubscriptionsReader subscriptionsReader;

    SubscriptionsChanged(final SubscriptionsReader subscriptionsReader) {
        this.subscriptionsReader = subscriptionsReader;
    }

    /**
     * The snapshot of local subscriptions.
     *
     * @return the snapshot.
     */
    public SubscriptionsReader getSubscriptionsReader() {
        return subscriptionsReader;
    }
}
