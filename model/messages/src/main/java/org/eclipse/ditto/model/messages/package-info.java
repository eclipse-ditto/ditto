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
 * Contains the Messages framework around the cornerstone of this package: {@link org.eclipse.ditto.model.messages.Message}.
 * A Message is sent <em>FROM</em> or <em>TO</em> a {@code Thing} or a {@code Feature}.
 *
 * <h3>Object creation</h3>
 * {@link org.eclipse.ditto.model.messages.MessagesModelFactory} is the main entry point for obtaining objects of this
 * package's interfaces to work with.
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault
package org.eclipse.ditto.model.messages;
