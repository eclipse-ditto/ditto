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
package org.eclipse.ditto.connectivity.service.mapping.javascript.benchmark;

import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Interface for scenarios mapping from an {@link ExternalMessage} to Ditto Protocol message.
 */
public interface MapToDittoProtocolScenario {

    Connection CONNECTION = TestConstants.createConnection();
    Config CONFIG = ConfigFactory.parseString("javascript {\n" +
                    "        maxScriptSizeBytes = 50000 # 50kB\n" +
                    "        maxScriptExecutionTime = 500ms\n" +
                    "        maxScriptStackDepth = 10\n" +
                    "      }").atKey("ditto.connectivity.mapping")
            .withFallback(ConfigFactory.load("test"));
    ConnectivityConfig CONNECTIVITY_CONFIG = ConnectivityConfig.of(CONFIG);

    MessageMapper getMessageMapper();

    ExternalMessage getExternalMessage();

}
