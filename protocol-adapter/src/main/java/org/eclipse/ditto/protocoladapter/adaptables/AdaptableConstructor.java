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

/* Copyright (c) 2011-2018 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.protocoladapter.adaptables;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.signals.base.WithId;

/**
 * @param <T> the type of the source signal
 */
public interface AdaptableConstructor<T extends WithId> {

    /**
     * @param signal the source signal from which to construct an {@link Adaptable}
     * @return an {@link Adaptable}
     */
    Adaptable construct(T signal, final TopicPath.Channel channel);

    default void validate(T signal) {
        // do nothing
    }

    TopicPathBuilder getTopicPathBuilder(T command);

    TopicPath.Action[] getSupportedActions();

    default void enhancePayloadBuilder(T command, PayloadBuilder payloadBuilder) {
        // do nothing
    }
}
