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

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario1;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario10;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario11;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario12;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario2;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario4;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario5;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario6;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario7;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario8;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario9;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1.Scenario1Simple;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1.Scenario1Simple1;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1.Scenario1Simple2;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1.Scenario1Simple3;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1.Scenario1Simple4;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested1;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested10;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested11;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested2;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested3;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested4;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested5;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested6;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested7;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested8;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested9;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke1;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke10;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke11;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke12;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke13;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke14;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke15;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke16;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke17;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke18;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke2;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke3;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke4;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke5;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke6;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke7;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke8;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke9;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects1;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects10;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects11;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects12;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects13;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects14;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects15;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects16;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects17;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects18;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects19;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects2;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects20;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects21;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects22;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects23;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects24;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects25;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects26;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects27;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects3;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects4;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects5;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects6;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects7;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects8;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects9;
import org.eclipse.ditto.policies.model.Policy;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
public abstract class AbstractPoliciesBenchmark {

    private static final int WARMUP_ITERATIONS = 10;
    private static final int MEASUREMENT_ITERATIONS = 10;
    private static final int WARMUP_TIME = 1000;
    private static final int MEASUREMENT_TIME = 1000;

    private final HashMap<String, PolicyAlgorithm> policyAlgorithms;

    public AbstractPoliciesBenchmark() {
        policyAlgorithms = new HashMap<>();
        policyAlgorithms.put(JsonViewScenario.SCENARIO_GROUP_NAME, getPolicyAlgorithm(JsonViewScenario.POLICY));
        policyAlgorithms.put(Scenario1Simple.SCENARIO_GROUP_NAME, getPolicyAlgorithm(Scenario1Simple.POLICY));
        policyAlgorithms.put(Scenario2Nested.SCENARIO_GROUP_NAME, getPolicyAlgorithm(Scenario2Nested.POLICY));
        policyAlgorithms.put(Scenario3Revoke.SCENARIO_GROUP_NAME, getPolicyAlgorithm(Scenario3Revoke.POLICY));
        policyAlgorithms.put(Scenario4MultipleSubjects.SCENARIO_GROUP_NAME,
                getPolicyAlgorithm(Scenario4MultipleSubjects.POLICY));
    }

    /**
     * Returns the PolicyAlgorithm to use for the benchmarks defined in this abstract class.
     *
     * @return the PolicyAlgorithm to use.
     */
    protected abstract PolicyAlgorithm getPolicyAlgorithm(final Policy policy);


    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario1Simple1(final Scenario1Simple1 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario1Simple2(final Scenario1Simple2 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario1Simple3(final Scenario1Simple3 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario1Simple4(final Scenario1Simple4 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested1(final Scenario2Nested1 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested2(final Scenario2Nested2 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested3(final Scenario2Nested3 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested4(final Scenario2Nested4 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested5(final Scenario2Nested5 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested6(final Scenario2Nested6 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested7(final Scenario2Nested7 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested8(final Scenario2Nested8 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested9(final Scenario2Nested9 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested10(final Scenario2Nested10 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario2Nested11(final Scenario2Nested11 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke1(final Scenario3Revoke1 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke2(final Scenario3Revoke2 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke3(final Scenario3Revoke3 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke4(final Scenario3Revoke4 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke5(final Scenario3Revoke5 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke6(final Scenario3Revoke6 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke7(final Scenario3Revoke7 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke8(final Scenario3Revoke8 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke9(final Scenario3Revoke9 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke10(final Scenario3Revoke10 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke11(final Scenario3Revoke11 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke12(final Scenario3Revoke12 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke13(final Scenario3Revoke13 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke14(final Scenario3Revoke14 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke15(final Scenario3Revoke15 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke16(final Scenario3Revoke16 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke17(final Scenario3Revoke17 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario3Revoke18(final Scenario3Revoke18 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects1(final Scenario4MultipleSubjects1 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects2(final Scenario4MultipleSubjects2 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects3(final Scenario4MultipleSubjects3 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects4(final Scenario4MultipleSubjects4 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects5(final Scenario4MultipleSubjects5 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects6(final Scenario4MultipleSubjects6 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects7(final Scenario4MultipleSubjects7 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects8(final Scenario4MultipleSubjects8 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects9(final Scenario4MultipleSubjects9 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects10(final Scenario4MultipleSubjects10 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects11(final Scenario4MultipleSubjects11 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects12(final Scenario4MultipleSubjects12 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects13(final Scenario4MultipleSubjects13 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects14(final Scenario4MultipleSubjects14 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects15(final Scenario4MultipleSubjects15 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects16(final Scenario4MultipleSubjects16 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects17(final Scenario4MultipleSubjects17 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects18(final Scenario4MultipleSubjects18 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects19(final Scenario4MultipleSubjects19 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects20(final Scenario4MultipleSubjects20 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects21(final Scenario4MultipleSubjects21 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects22(final Scenario4MultipleSubjects22 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects23(final Scenario4MultipleSubjects23 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects24(final Scenario4MultipleSubjects24 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects25(final Scenario4MultipleSubjects25 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects26(final Scenario4MultipleSubjects26 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_Scenario4MultipleSubjects27(final Scenario4MultipleSubjects27 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario1(final JsonViewScenario1 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario2(final JsonViewScenario2 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario4(final JsonViewScenario4 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario5(final JsonViewScenario5 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario6(final JsonViewScenario6 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario7(final JsonViewScenario7 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario8(final JsonViewScenario8 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario9(final JsonViewScenario9 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario10(final JsonViewScenario10 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario11(final JsonViewScenario11 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    @Benchmark
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME, timeUnit = TimeUnit.MILLISECONDS)
    public boolean benchmark_JsonViewScenario12(final JsonViewScenario12 scenario) {
        return runScenarioWithAlgorithm(scenario);
    }

    private boolean runScenarioWithAlgorithm(final Scenario scenario) {
        final PolicyAlgorithm algorithm = policyAlgorithms.get(scenario.getScenarioGroup());
        final ScenarioSetup setup = scenario.getSetup();
        final boolean authorized = scenario.getApplyAlgorithmFunction().apply(algorithm);
        if (authorized != setup.getExpectedResult()) {
            throw new IllegalStateException("ScenarioSetup \n'" + setup +
                    "'\ndid not result in expected result when running with Algorithm '" + algorithm.getName() + "'");
        }

        setup.getAdditionalAlgorithmFunction().ifPresent(algorithmFunction -> {
            final Boolean passed = algorithmFunction.apply(algorithm);
            if (!passed) {
                throw new AssertionError("AlgorithmFunction which was expected to pass did not pass!");
            }
        });

        final Optional<JsonObject> jsonView = setup.getFullJsonObject().map(jsonObject ->
                algorithm.buildJsonView(jsonObject, setup)
        );
        setup.getExpectedJsonView().ifPresent(expectedView -> {
            if (!expectedView.toString().equals(jsonView.orElseThrow(() -> new AssertionError("jsonView was empty")).toString())) {
                throw new AssertionError("Json View was not equal!");
            }
        });

        return authorized;
    }
}
