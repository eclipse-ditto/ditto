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
package org.eclipse.ditto.internal.utils.pubsub.api;

import java.util.Set;

/**
 * Super class of subscription requests.
 */
public interface Request {

    /**
     * @return topics in the subscription.
     */
    Set<String> getTopics();

    /**
     * @return whether acknowledgement is expected.
     */
    boolean shouldAcknowledge();

}
