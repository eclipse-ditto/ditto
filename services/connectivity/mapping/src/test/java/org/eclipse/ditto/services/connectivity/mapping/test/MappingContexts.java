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
package org.eclipse.ditto.services.connectivity.mapping.test;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;

public class MappingContexts {

    public static MappingContext mock(final String engine, Map<String, String> options)
    {
        final Map<String, String> opts = new HashMap<>(options);
        return ConnectivityModelFactory.newMappingContext(engine, opts);
    }

    public static MappingContext mock(final Class<? extends MessageMapper> messageMapperClass,
            Map<String, String> opts)
    {
        return mock(messageMapperClass.getCanonicalName(), opts);
    }

    public static MappingContext mock(final boolean isValid)
    {
        final Map<String, String> opts = new HashMap<>();
        opts.put(MockMapper.OPT_IS_VALID, String.valueOf(isValid));
        return mock(MockMapper.class, opts);
    }
}
