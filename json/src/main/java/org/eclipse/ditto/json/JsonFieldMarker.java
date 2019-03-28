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
package org.eclipse.ditto.json;

/**
 * Marker interface to provide a common type for markers of JSON fields. A marker has no meaning of its own. It is up to
 * the users of Ditto JSON to provide semantics to a marker. For example, a marker could express that the marked JSON
 * field is part of a particular schema version or that the marked field is special for some reason.
 */
public interface JsonFieldMarker {
}
