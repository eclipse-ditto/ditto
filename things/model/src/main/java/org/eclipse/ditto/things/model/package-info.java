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
 * Contains the Things framework around the cornerstone of this package: {@link org.eclipse.ditto.things.model.Thing}.
 * A Thing can consist of
 * <ul>
 * <li>{@link org.eclipse.ditto.things.model.Attributes} for describing the Thing in more details,</li>
 * <li>and {@link org.eclipse.ditto.things.model.Features} for managing all data and functionality of the Thing.</li>
 * </ul>
 * <p>
 * Furthermore a Thing can have a {@link org.eclipse.ditto.things.model.ThingLifecycle} and a
 * {@link org.eclipse.ditto.things.model.ThingRevision} which is incremented with each change of the Thing.
 *
 * <h2>Object creation</h2>
 * {@link org.eclipse.ditto.things.model.ThingsModelFactory} is the main entry point for obtaining objects of this
 * package's interfaces to work with.
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault
package org.eclipse.ditto.things.model;
