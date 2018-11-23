/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity;

import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Represents an inbound {@link ExternalMessage} i.e. a message that is recevied by Ditto. It contains the
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
