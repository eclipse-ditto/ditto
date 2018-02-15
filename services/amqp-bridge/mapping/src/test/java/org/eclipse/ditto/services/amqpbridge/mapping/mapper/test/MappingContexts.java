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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper.test;

import java.util.Collections;

import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.MappingContext;

public class MappingContexts {

    public final static MappingContext NOOP_FUNCTION =
            AmqpBridgeModelFactory.newMappingContext("NoOpMapper", "NoOpMapper", Collections.emptyMap());

    public final static MappingContext NOOP_CLASS = AmqpBridgeModelFactory.newMappingContext("NoOpMapper",
            NoOpMapper.class.getCanonicalName(), Collections.emptyMap());

    public final static MappingContext MISSING_MAPPER = AmqpBridgeModelFactory.newMappingContext("MissingMapper",
            "MissingMapper", Collections.emptyMap());

    public final static MappingContext ILLEGAL_MAPPER = AmqpBridgeModelFactory.newMappingContext("IllegalMapper",
            MappingContext.class.getCanonicalName(), Collections.emptyMap());
}
