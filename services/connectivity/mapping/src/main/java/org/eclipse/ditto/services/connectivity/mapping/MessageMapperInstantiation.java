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
package org.eclipse.ditto.services.connectivity.mapping;

import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.MappingContext;

import akka.actor.DynamicAccess;

/**
 * Interface for dynamic message mapper instantiation.
 */
@FunctionalInterface
public interface MessageMapperInstantiation extends BiFunction<MappingContext, DynamicAccess, MessageMapper> {

    /**
     * Instantiate a message mapper.
     *
     * @param mappingContext the mapping context that configures the mapper.
     * @param dynamicAccess dynamic access to load classes in an actor system.
     * @return an instantiated message mapper according to the mapping context if instantiation is possible, or null
     * otherwise.
     */
    @Nullable
    @Override
    MessageMapper apply(MappingContext mappingContext, DynamicAccess dynamicAccess);
}
