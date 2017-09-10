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

/**
 * The Ditto Protocol Adapter provides a wrapper for the Ditto internal protocol. This package provides the
 * corresponding interfaces as well as implementations.
 * <p>
 * Please note that Ditto Protocol Adapter is designed for <em>immutability</em>. Therefore each object is immutable and
 * thus thread safe. If interfaces provide methods for altering an object then invoking this method does not change the
 * state of that object but a new object with the altered state is returned instead. This is the same behavior like it
 * is shown by java.lang.String for example.
 */
package org.eclipse.ditto.protocoladapter;
