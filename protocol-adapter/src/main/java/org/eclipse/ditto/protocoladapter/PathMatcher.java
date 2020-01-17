/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

/* Copyright (c) 2011-2018 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.json.JsonPointer;

public interface PathMatcher {

    String match(JsonPointer path);
}
