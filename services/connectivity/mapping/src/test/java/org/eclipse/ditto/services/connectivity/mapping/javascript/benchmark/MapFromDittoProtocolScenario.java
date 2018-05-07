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

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;

/**
 * Interface for scenarios mapping from an Ditto {@link Adaptable} to an external message.
 */
public interface MapFromDittoProtocolScenario {

    MessageMapper getMessageMapper();

    Adaptable getDittoAdaptable();
}
