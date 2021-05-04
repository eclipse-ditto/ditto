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

/**
 * The Ditto Protocol Adapter provides a wrapper for the Ditto internal protocol. This package provides the
 * corresponding interfaces as well as implementations.
 * <p>
 * Please note that Ditto Protocol Adapter is designed for <em>immutability</em>. Therefore each object is immutable and
 * thus thread safe. If interfaces provide methods for altering an object then invoking this method does not change the
 * state of that object but a new object with the altered state is returned instead. This is the same behavior like it
 * is shown by java.lang.String for example.
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault
package org.eclipse.ditto.protocol;
