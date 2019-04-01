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
