/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.auth;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.AwsCredential;
import com.mongodb.MongoCredential;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * Helper class to obtain {@link MongoCredential}s when running Ditto in AWS.
 */
public final class AwsAuthenticationHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsAuthenticationHelper.class);

    private AwsAuthenticationHelper() {
        throw new AssertionError();
    }

    /**
     * Obtains a {@link MongoCredential} based on AWS IAM "obtaining role" based authentication.
     *
     * @param awsRegion the optional to-be-configured AWS region
     * @param awsRoleArn the role ARN to obtain
     * @param awsSessionName the session name to use
     * @return the MongoCredential prepared to authenticate via AWS IAM
     */
    public static MongoCredential provideAwsIamBasedMongoCredential(
            @Nullable final String awsRegion,
            final String awsRoleArn,
            final String awsSessionName
    ) {
        final StsClientBuilder stsClientBuilder = StsClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (awsRegion != null && !awsRegion.isEmpty()) {
            stsClientBuilder.region(Region.of(awsRegion));
        }

        final Supplier<AwsCredential> awsFreshCredentialSupplier;
        try (final StsClient stsClient = stsClientBuilder.build()) {
            awsFreshCredentialSupplier = () -> {
                LOGGER.info("Supplying AWS IAM credentials, assuming role <{}> in session name <{}>",
                        awsRoleArn, awsSessionName);

                // assume role using the AWS SDK
                final AssumeRoleRequest roleRequest = AssumeRoleRequest.builder()
                        .roleArn(awsRoleArn)
                        .roleSessionName(awsSessionName)
                        .build();
                final AssumeRoleResponse roleResponse = stsClient.assumeRole(roleRequest);
                final Credentials awsCredentials = roleResponse.credentials();

                return new AwsCredential(awsCredentials.accessKeyId(), awsCredentials.secretAccessKey(),
                        awsCredentials.sessionToken());
            };

            return MongoCredential.createAwsCredential(null, null)
                    .withMechanismProperty(MongoCredential.AWS_CREDENTIAL_PROVIDER_KEY, awsFreshCredentialSupplier);
        }
    }
}
