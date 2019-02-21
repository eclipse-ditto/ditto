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
package org.eclipse.ditto.services.connectivity.mapping.javascript.benchmark;

import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

import com.typesafe.config.ConfigFactory;

/**
 * Interface for scenarios mapping from an {@link ExternalMessage} to Ditto Protocol message.
 */
public interface MapToDittoProtocolScenario {

    MappingConfig MAPPING_CONFIG =
            DefaultMappingConfig.of(ConfigFactory.parseString("javascript {\n" +
                    "        maxScriptSizeBytes = 50000 # 50kB\n" +
                    "        maxScriptExecutionTime = 500ms\n" +
                    "        maxScriptStackDepth = 10\n" +
                    "      }"));

    MessageMapper getMessageMapper();

    ExternalMessage getExternalMessage();

}
