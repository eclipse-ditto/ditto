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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable builder for a {@link ImmutablePolicy} with a fluent API scoped to a specified {@link Label}.
 */
@NotThreadSafe
final class ImmutablePolicyBuilderLabelScoped extends AbstractPolicyBuilderLabelScoped {

    private ImmutablePolicyBuilderLabelScoped(final PolicyBuilder delegate, final Label label) {
        super(delegate, label);
    }

    /**
     * Returns a new empty builder for a {@code Policy} but scoped to the provided {@code label}.
     *
     * @return the new builder.
     */
    public static LabelScoped newInstance(final PolicyBuilder delegate, final Label label) {
        return new ImmutablePolicyBuilderLabelScoped(checkNotNull(delegate, "delegate"), checkNotNull(label, "label"));
    }

}
