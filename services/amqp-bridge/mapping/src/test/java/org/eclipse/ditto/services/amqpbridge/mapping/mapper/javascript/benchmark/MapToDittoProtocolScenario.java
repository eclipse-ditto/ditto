/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript.benchmark;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;

/**
 * Interface for scenarios mapping from an {@link ExternalMessage} to Ditto Protocol message.
 */
public interface MapToDittoProtocolScenario {

    MessageMapper getMessageMapper();

    ExternalMessage getExternalMessage();
}
