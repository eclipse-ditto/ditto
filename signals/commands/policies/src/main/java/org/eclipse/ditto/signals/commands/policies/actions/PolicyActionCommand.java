/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.policies.actions;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

/**
 * Interface for policy action commands requiring the EXECUTE permission.
 *
 * @param <T> the type of the implementing class.
 * @since 2.0.0
 */
public interface PolicyActionCommand<T extends PolicyActionCommand<T>> extends PolicyCommand<T>, WithOptionalEntity {

    /**
     * Path of Policy actions as part of the the {@link #getResourcePath()}.
     */
    JsonPointer RESOURCE_PATH_ACTIONS = JsonPointer.of("actions");

    /**
     * Get the subject ID of this command.
     *
     * @return the subject ID.
     */
    SubjectId getSubjectId();

    /**
     * Set the label of the policy entry where this action is executed, if applicable.
     *
     * @param label the policy entry label to execute the action.
     * @return a new command to execute the action at the new policy entry if applicable, or this object if not.
     */
    T setLabel(Label label);

    /**
     * Check if this command is applicable to a policy entry.
     *
     * @param policyEntry the policy entry.
     * @return whether this command is applicable.
     */
    boolean isApplicable(PolicyEntry policyEntry);

    @Override
    default Category getCategory() {
        return Category.ACTION;
    }
}
