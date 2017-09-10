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
package org.eclipse.ditto.json;

/**
 * Marker interface to provide a common type for markers of JSON fields. A marker has no meaning of its own. It is up to
 * the users of Ditto JSON to provide semantics to a marker. For example, a marker could express that the marked JSON
 * field is part of a particular schema version or that the marked field is special for some reason.
 */
public interface JsonFieldMarker {
}
