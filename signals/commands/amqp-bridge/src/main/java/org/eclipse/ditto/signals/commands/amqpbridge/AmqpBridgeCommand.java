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
package org.eclipse.ditto.signals.commands.amqpbridge;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Base interface for all commands which are understood by the AMQP Bridge service. Implementations of this interface
 * are required to be immutable.
 *
 * @param <T> the type of the implementing class.
 */
public interface AmqpBridgeCommand<T extends AmqpBridgeCommand> extends Command<T> {

    /**
     * Type Prefix of AmqpBridge commands.
     */
    String TYPE_PREFIX = "amqp.bridge." + TYPE_QUALIFIER + ":";

    /**
     * AmqpBridge resource type.
     */
    String RESOURCE_TYPE = "amqp.bridge";

    /**
     * Returns the identifier of the Connection.
     *
     * @return the identifier of the Connection.
     */
    String getConnectionId();

    @Override
    default String getId() {
        return getConnectionId();
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonFactory.emptyPointer();
    }

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

}
