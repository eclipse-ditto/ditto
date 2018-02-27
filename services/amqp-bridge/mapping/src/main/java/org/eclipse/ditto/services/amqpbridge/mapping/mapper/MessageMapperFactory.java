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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.model.amqpbridge.MappingContext;

public interface MessageMapperFactory {

    Optional<MessageMapper> mapperOf(final MappingContext context);

    List<MessageMapper> mappersOf(final List<MappingContext> contexts);

    MessageMapperRegistry registryOf(final List<MappingContext> contexts);
}
