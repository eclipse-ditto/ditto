---
title: Operating - MongoDB
tags: [installation]
keywords: operating, mongodb, database, configuration, tuning, indexes
permalink: operating-mongodb.html
---

You configure Ditto's MongoDB connection and tune database performance through environment variables and config files.

{% include callout.html content="**TL;DR**: Set `MONGO_DB_URI` and related environment variables to connect to your MongoDB instance. For MongoDB 6+, enable additional snapshot aggregation indexes to maintain query performance." type="primary" %}

## Overview

Each Ditto microservice that persists data connects to MongoDB. If you use a dedicated MongoDB instance instead of the bundled container, you control the connection through environment variables.

## Configuration

### Connection settings

Set these environment variables to configure the MongoDB connection:

| Variable | Purpose |
|----------|---------|
| `MONGO_DB_URI` | Connection string to MongoDB |
| `MONGO_DB_SSL_ENABLED` | Enable SSL connection |
| `MONGO_DB_CONNECTION_MIN_POOL_SIZE` | Minimum connection pool size |
| `MONGO_DB_CONNECTION_POOL_SIZE` | Connection pool size |
| `MONGO_DB_READ_PREFERENCE` | Read preference setting |
| `MONGO_DB_WRITE_CONCERN` | Write concern setting |
| `PEKKO_PERSISTENCE_MONGO_JOURNAL_WRITE_CONCERN` | Pekko Persistence journal write concern |
| `PEKKO_PERSISTENCE_MONGO_SNAPS_WRITE_CONCERN` | Pekko Persistence snapshot write concern |

### Passwordless authentication via AWS IAM

Since Ditto 3.6.0, you can [authenticate with AWS IAM](https://www.mongodb.com/docs/atlas/security/aws-iam-authentication/) instead of using username/password credentials.

To enable this:

1. Configure your Kubernetes service account with the role ARN via the annotation `eks.amazonaws.com/role-arn`.
2. Configure Ditto's services to assume that role during MongoDB authentication.

**Environment variables:**

| Variable | Purpose |
|----------|---------|
| `MONGO_DB_USE_AWS_IAM_ROLE` | Enable AWS IAM role authentication (boolean) |
| `MONGO_DB_AWS_REGION` | AWS region |
| `MONGO_DB_AWS_ROLE_ARN` | ARN of the IAM role to assume |
| `MONGO_DB_AWS_SESSION_NAME` | AWS session name for the assumed role |

**Helm chart:** Configure the `serviceAccount` key to annotate the Kubernetes service account, and the `dbconfig` key for each Ditto service in your `values.yaml`.

## Tuning

### Background aggregation queries

Ditto runs background `aggregate` queries against MongoDB to clean up data based on current database load. These queries primarily target snapshot collections (`things_snaps`, `policies_snaps`, `connections_snaps`).

#### MongoDB 5

The default settings work well. Aggregation queries run quickly without excessive disk read operations.

#### MongoDB 6+

In MongoDB 6, the same aggregation queries can slow down significantly with large snapshot stores, causing increased disk read IOPS. Enable additional indexes to restore performance:

**Environment variables:**

```bash
MONGODB_READ_JOURNAL_SHOULD_CREATE_ADDITIONAL_SNAPSHOT_AGGREGATION_INDEX_PID_ID=true
MONGODB_READ_JOURNAL_SHOULD_CREATE_ADDITIONAL_SNAPSHOT_AGGREGATION_INDEX_PID_SN=true
MONGODB_READ_JOURNAL_SHOULD_CREATE_ADDITIONAL_SNAPSHOT_AGGREGATION_INDEX_PID_SN_ID=true
```

**Helm values (example for the `things` service):**

```yaml
things:
  config:
    readJournal:
      indexes:
        createSnapshotAggregationIndexPidId: true
        createSnapshotAggregationIndexPidSn: true
        createSnapshotAggregationIndexPidSnId: true
```

### Background cleanup

Ditto deletes unnecessary events and snapshots in the background according to database load. This cleanup is available for Policies, Things, and Connectivity services.

Key configuration parameters:

```hocon
cleanup {
  enabled = true
  enabled = ${?CLEANUP_ENABLED}

  # How long to keep events/snapshots before cleanup
  history-retention-duration = 3d
  history-retention-duration = ${?CLEANUP_HISTORY_RETENTION_DURATION}

  # Pause between cleanup runs
  quiet-period = 5m
  quiet-period = ${?CLEANUP_QUIET_PERIOD}

  # How often to issue cleanup credits
  interval = 3s
  interval = ${?CLEANUP_INTERVAL}

  # Max DB latency before pausing cleanup
  timer-threshold = 150ms
  timer-threshold = ${?CLEANUP_TIMER_THRESHOLD}

  # Credits issued per interval
  credits-per-batch = 3
  credits-per-batch = ${?CLEANUP_CREDITS_PER_BATCH}

  # Snapshots scanned per query
  reads-per-query = 100
  reads-per-query = ${?CLEANUP_READS_PER_QUERY}

  # Documents deleted per credit
  writes-per-credit = 100
  writes-per-credit = ${?CLEANUP_WRITES_PER_CREDIT}

  # Whether to delete the final "deleted" snapshot
  delete-final-deleted-snapshot = false
  delete-final-deleted-snapshot = ${?CLEANUP_DELETE_FINAL_DELETED_SNAPSHOT}
}
```

By default, the retention duration is `0d`, meaning no history is kept. To use Ditto's [history capabilities](basic-history.html), adjust `history-retention-duration` accordingly.

You can also manage background cleanup at runtime through [DevOps piggyback commands](operating-devops.html).

## Further reading

* [Operating - Configuration](operating-configuration.html)
* [Operating - DevOps Commands](operating-devops.html)
* [Operating - Monitoring & Tracing](operating-monitoring.html)
