/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.Set;

import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Represents an outbound signal i.e. a signal that is sent from Ditto to an external target.
 */
public interface OutboundSignal {

    /**
     * @return the originating signal.
     */
    Signal<?> getSource();

    /**
     * @return the targets that are authorized to read and subscribed for the outbound signal.
     */
    Set<Target> getTargets();

    /**
     * Extends the {@link OutboundSignal} by adding the mapped message.
     */
    interface WithExternalMessage extends OutboundSignal {

        /**
         * @return the {@link ExternalMessage} that was mapped from the outbound signal.
         */
        ExternalMessage getExternalMessage();
    }

}
