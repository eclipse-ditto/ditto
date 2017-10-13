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
 * Ditto JSON is a general purpose JSON library. This package provides the corresponding interfaces as well as
 * implementations. There is one central place for obtaining instances of the various interfaces:
 * {@link org.eclipse.ditto.json.JsonFactory}. JsonFactory provides only {@code static} methods which means that they
 * can be imported statically and by thus make calls concise to assist the readability of your code.
 * <p>
 * Please note that Ditto JSON is designed for <em>immutability</em>. Therefore each object returned by JsonFactory -
 * apart from builders - is immutable and thus thread safe. If interfaces provide methods for altering an object then
 * invoking this method does not change the state of that object but a new object with the altered state is returned
 * instead. This is the same behavior like it is shown by java.lang.String for example.
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault
package org.eclipse.ditto.json;
