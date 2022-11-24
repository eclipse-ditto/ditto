/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;

/**
 * A supervisor internal message which contains the {@link org.eclipse.ditto.internal.utils.pubsub.DistributedPub} to publish the
 * {@code wrappedSignalForPublication} to - which is the also passed in {@code signal} wrapped using
 * {@link org.eclipse.ditto.internal.utils.pubsub.DistributedPub#wrapForPublicationWithAcks(Object, CharSequence, org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor)}
 * with the specific ack extractor for that signal type.
 *
 * @param pub the DistributedPub to use for publishing the message
 * @param wrappedSignalForPublication the wrapped signal to publish
 * @param signal the original, not yet wrapped signal
 */
public record DistributedPubWithMessage(DistributedPub<?> pub,
                                        Object wrappedSignalForPublication,
                                        Signal<?> signal) {}
