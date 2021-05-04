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
package org.eclipse.ditto.connectivity.api;

import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Represents an inbound {@link ExternalMessage} i.e. a message that is received by Ditto. It contains the
 * original external message, the mapped topic path and the resulting signal of the message mapping.
 */
public interface InboundExternalMessage {

    /**
     * @return the originating {@link ExternalMessage}.
     */
    ExternalMessage getSource();

    /**
     * @return the topic path of the mapped message.
     */
    TopicPath getTopicPath();

    /**
     * @return the {@link Signal} that was mapped from the inbound {@link ExternalMessage}.
     */
    Signal<?> getSignal();
}
