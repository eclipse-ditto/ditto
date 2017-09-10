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
package org.eclipse.ditto.model.policiesenforcers.testbench;

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policiesenforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.model.policiesenforcers.testbench.algorithms.TreeBasedPolicyAlgorithm;


public class TreeBasedPolicyAlgorithmBenchmark extends AbstractPoliciesBenchmark {

    @Override
    protected PolicyAlgorithm getPolicyAlgorithm(final Policy policy) {
        return new TreeBasedPolicyAlgorithm(policy);
    }
}
