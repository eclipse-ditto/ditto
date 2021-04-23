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

import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.protocol.Adaptable;

/**
 * Interface for scenarios mapping from an Ditto {@link Adaptable} to an external message.
 */
public interface MapFromDittoProtocolScenario {

    MessageMapper getMessageMapper();

    Adaptable getDittoAdaptable();
}
