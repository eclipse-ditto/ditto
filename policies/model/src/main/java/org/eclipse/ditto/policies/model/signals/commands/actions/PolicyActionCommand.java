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
package org.eclipse.ditto.policies.model.signals.commands.actions;

import java.util.Set;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyActionFailedException;

/**
 * Interface for policy action commands requiring the EXECUTE permission.
 *
 * @param <T> the type of the implementing class.
 * @since 2.0.0
 */
public interface PolicyActionCommand<T extends PolicyActionCommand<T>> extends PolicyCommand<T>, WithOptionalEntity {

    /**
     * Path of Policy actions as part of the {@link #getResourcePath()}.
     */
    JsonPointer RESOURCE_PATH_ACTIONS = JsonPointer.of("actions");

    /**
     * Get the subject IDs of this command.
     *
     * @return the subject IDs.
     */
    Set<SubjectId> getSubjectIds();

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
     * @param authorizationContext the AuthorizationContext containing the authenticated subjects of the command.
     * @return whether this command is applicable.
     */
    boolean isApplicable(PolicyEntry policyEntry, AuthorizationContext authorizationContext);

    /**
     * Get the exception for when a policy action command is not applicable (after having invoked
     * {@link #isApplicable(org.eclipse.ditto.policies.model.PolicyEntry, org.eclipse.ditto.base.model.auth.AuthorizationContext)}) to the designated policy entry.
     *
     * @param dittoHeaders headers of the exception.
     * @return the exception for when a policy action command is not applicable.
     */
    PolicyActionFailedException getNotApplicableException(DittoHeaders dittoHeaders);

    @Override
    default Category getCategory() {
        return Category.ACTION;
    }
}
