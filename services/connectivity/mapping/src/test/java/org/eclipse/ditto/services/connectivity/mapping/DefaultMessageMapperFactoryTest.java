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
package org.eclipse.ditto.services.connectivity.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.services.connectivity.mapping.test.MappingContexts;
import org.eclipse.ditto.services.connectivity.mapping.test.MockMapper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link DefaultMessageMapperFactory}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultMessageMapperFactoryTest {

    private static final String KNOWN_FACTORY_NAME = "org.eclipse.ditto.services.connectivity.mapping.test.Mappers";

    private static MappingConfig mappingConfig;
    private static ActorSystem system;

    @Mock
    private DiagnosticLoggingAdapter log;

    private DefaultMessageMapperFactory underTest;

    @BeforeClass
    public static void initTestFixture() {
        final Config testConfig = ConfigFactory.parseMap(
                Collections.singletonMap("ditto.connectivity.mapping.factory", KNOWN_FACTORY_NAME));
        mappingConfig = DefaultMappingConfig.of(testConfig.getConfig("ditto.connectivity"));

        system = ActorSystem.create("test", testConfig);
    }

    @AfterClass
    public static void shutDownActorSystem() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Before
    public void setUp() {
        underTest = DefaultMessageMapperFactory.of(DefaultEntityId.of("connectionId"), system, mappingConfig, log);
    }

    @After
    public void tearDown() {
        underTest = null;
    }

    @Test
    public void loadMapperFromFactoryMethod() {
        final Map<String, String> options = new HashMap<>();
        // add this to pass configuration checks
        options.put(MockMapper.OPT_IS_VALID, String.valueOf(true));

        // looking for a method containing 'test' and returning a MessageMapper in Mappers.class
        final MappingContext ctx = MappingContexts.mock("test", options);

        assertThat(underTest.mapperOf(ctx)).isPresent();
    }

    @Test
    public void loadMapperFromClass() {
        final MappingContext ctx = MappingContexts.mock(true);

        assertThat(underTest.mapperOf(ctx)).isPresent();
    }

    @Test
    public void loadMissingMapper() {
        final MappingContext ctx = MappingContexts.mock("not-a-class", Collections.emptyMap());

        assertThat(underTest.mapperOf(ctx)).isEmpty();
    }

    @Test
    public void loadNonMessageMapperClass() {
        final MappingContext ctx = MappingContexts.mock(String.class.getCanonicalName(),
                Collections.emptyMap());

        assertThat(underTest.mapperOf(ctx)).isEmpty();
    }

    @Test
    public void createWithFactoryMethod() {
        // looking for a method containing 'test' and returning a MessageMapper in Mappers.class
        final MappingContext ctx = MappingContexts.mock("test", Collections.emptyMap());
        assertThat(underTest.createMessageMapperInstance(ctx)).isPresent();
    }

    @Test
    public void createWithFactoryMethodFindsNoMethod() {
        // looking for a message containing 'strong-smell-wasabi' which does not exist in Mappers.class and therefore
        // expect an empty optional
        final MappingContext ctx = MappingContexts.mock("strong-smell-wasabi",
                Collections.emptyMap());

        assertThat(underTest.createMessageMapperInstance(ctx)).isEmpty();
    }

    @Test
    public void createWithClassName() {
        // MockMapper extends MessageMapper and can be loaded
        final MappingContext ctx = MappingContexts.mock(MockMapper.class, Collections.emptyMap());

        assertThat(underTest.createMessageMapperInstance(ctx)).isPresent();
    }

    @Test
    public void createWithClassNameFindsNoClass() {
        final MappingContext ctx = MappingContexts.mock("not-a-class", Collections.emptyMap());

        assertThat(underTest.createMessageMapperInstance(ctx)).isEmpty();
    }

    @Test
    public void createWithClassNameFailsForNonMapperClass() {
        // load string as a MessageMapper -> should fail
        final MappingContext ctx = MappingContexts.mock(String.class.getCanonicalName(), Collections.emptyMap());

        assertThat(underTest.createMessageMapperInstance(ctx)).isEmpty();
    }

    @Test
    public void loadMapperWithInvalidConfig() {
        final MappingContext ctx = MappingContexts.mock(false);
        assertThat(underTest.mapperOf(ctx)).isEmpty();
    }

    @Test
    public void loadMapperWithoutContentType() {
        final Map<String, String> opts = new HashMap<>();

        final MappingContext ctx =
                ConnectivityModelFactory.newMappingContext(MockMapper.class.getCanonicalName(), opts);
        assertThat(underTest.mapperOf(ctx)).isEmpty();
    }

    @Test
    public void loadRegistry() {
        final MappingContext fooCtx = MappingContexts.mock(true);

        final MessageMapperRegistry underTest = this.underTest.registryOf(DittoMessageMapper.CONTEXT, fooCtx);
        assertThat(underTest.getMapper().get().getClass()).isEqualTo(WrappingMessageMapper.class);
        assertThat(((WrappingMessageMapper) underTest.getMapper().get()).getDelegate().getClass()).isEqualTo(
                MockMapper.class);
        assertThat(underTest.getDefaultMapper().getClass()).isEqualTo(WrappingMessageMapper.class);
        assertThat(((WrappingMessageMapper) underTest.getDefaultMapper()).getDelegate().getClass()).isEqualTo(
                DittoMessageMapper.class);
    }

}
