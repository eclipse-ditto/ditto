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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperConfiguration;

/**
 * TODO doc
 */
public interface JavaScriptMessageMapperMapperOptions extends MessageMapperConfiguration {

    Optional<String> getIncomingMappingScript();

    Optional<String> getOutgoingMappingScript();

    boolean isLoadBytebufferJS();

    boolean isLoadLongJS();

    boolean isLoadMustacheJS();

    /**
     *
     */
    interface Builder {

        Builder incomingMappingScript(@Nullable String mappingScript);

        Builder outgoingMappingScript(@Nullable String mappingScript);

        Builder loadBytebufferJS(boolean load);

        Builder loadLongJS(boolean load);

        Builder loadMustacheJS(boolean load);

        JavaScriptMessageMapperMapperOptions build();
    }
}
