/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.things;

import org.eclipse.ditto.protocoladapter.AbstractAdapter;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.UnknownPathException;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategies;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.common.PathMatcher;
import org.eclipse.ditto.signals.commands.common.PathPatterns;
import org.eclipse.ditto.signals.commands.common.ThingModifyPathMatcher;

/**
 * Base class for {@link org.eclipse.ditto.protocoladapter.Adapter}s that handle thing commands.
 *
 * @param <T> the type of the thing commands
 */
abstract class AbstractThingAdapter<T extends Signal<?>> extends AbstractAdapter<T> implements ThingAdapter<T> {

    /**
     * Constructor.
     *
     * @param mappingStrategies the mapping strategies used to convert from
     * {@link org.eclipse.ditto.protocoladapter.Adaptable}s to {@link org.eclipse.ditto.signals.base.Signal}s
     * @param headerTranslator the header translator used for the mapping
     */
    protected AbstractThingAdapter(final MappingStrategies<T> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        this(mappingStrategies, headerTranslator,
                ThingModifyPathMatcher.getInstance(path -> UnknownPathException.newBuilder(path).build()));
    }

    /**
     * Constructor.
     *
     * @param mappingStrategies the mapping strategies used to convert from
     * {@link org.eclipse.ditto.protocoladapter.Adaptable}s to {@link org.eclipse.ditto.signals.base.Signal}s
     * @param headerTranslator the header translator used for the mapping
     * @param pathMatcher the path matcher used for the mapping
     */
    protected AbstractThingAdapter(final MappingStrategies<T> mappingStrategies,
            final HeaderTranslator headerTranslator, final PathMatcher<PathPatterns> pathMatcher) {
        super(mappingStrategies, headerTranslator, pathMatcher);
    }

}
