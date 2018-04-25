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
package org.eclipse.ditto.services.connectivity.mapping.javascript.benchmark;

import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Interface for scenarios mapping from an {@link ExternalMessage} to Ditto Protocol message.
 */
public interface MapToDittoProtocolScenario {

    Config MAPPING_CONFIG = ConfigFactory.parseString("javascript {\n" +
            "        maxScriptSizeBytes = 50000 # 50kB\n" +
            "        maxScriptExecutionTime = 500ms\n" +
            "        maxScriptStackDepth = 10\n" +
            "      }");

    MessageMapper getMessageMapper();

    ExternalMessage getExternalMessage();
}
