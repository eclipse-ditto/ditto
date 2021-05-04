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
package org.eclipse.ditto.policies.model.enforcers.testbench;

import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.TreeBasedPolicyAlgorithm;
import org.eclipse.ditto.policies.model.Policy;


public class TreeBasedPolicyAlgorithmBenchmark extends AbstractPoliciesBenchmark {

    @Override
    protected PolicyAlgorithm getPolicyAlgorithm(final Policy policy) {
        return new TreeBasedPolicyAlgorithm(policy);
    }
}
