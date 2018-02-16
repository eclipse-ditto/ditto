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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;

public class MappingContexts {

    public static MappingContext mock(final String contentType, final boolean
            isContentTypeRequired, final String engine, Map<String, String> options)
    {
        Map<String, String> opts = new HashMap<>(options);
        opts.put(MessageMapper.OPT_CONTENT_TYPE, contentType);
        opts.put(MessageMapper.OPT_CONTENT_TYPE_REQUIRED, String.valueOf(isContentTypeRequired));

        return AmqpBridgeModelFactory.newMappingContext(contentType, engine, opts);
    }

    public static MappingContext mock(final String contentType, final boolean
            isContentTypeRequired, final Class<? extends MessageMapper> messageMapperClass, Map<String, String> opts)
    {
        return mock(contentType, isContentTypeRequired, messageMapperClass.getCanonicalName(), opts);
    }

    public static MappingContext mock(final String contentType, final boolean isContentTypeRequired,
            final boolean isValid)
    {
        Map<String, String> opts = new HashMap<>();
        opts.put(MockMapper.OPT_IS_VALID, String.valueOf(isValid));
        return mock(contentType, isContentTypeRequired, MockMapper.class, opts);
    }
}
