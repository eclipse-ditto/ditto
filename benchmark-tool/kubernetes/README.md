This benchmark is done via the load-testing tool k6 - https://github.com/grafana/k6 with the xk6-kafka extension - https://github.com/mostafa/xk6-kafka

Monster mock is used to mock http responses, it exposes 2 endpoints that are configurable via mmock/default.yaml and mmock/live_messages.yaml. Default values are /:thingId and /live_messages and they are used to publish modified things events and device live messages sent via HTTP POST request.

The k6 benchmark test consists of 5 tests available to run, called scenarios:

- DEVICE_LIVE_MESSAGES - Send live messages to things via HTTP. Ditto HTTP push connection is configured in ditto, which sends events from topic _**/_/things/live/messages** to a monster mock endpoint, which replies with predefined ditto protocol message.

- SEARCH_THINGS - search things via HTTP ( get things by applying filter )

- READ_THINGS - read things via HTTP ( get things by id )

- MODIFY_THINGS - Modify things by sending kafka messages to specfic topic. Ditto kafka connection is reading from this topic and processes the messages. Ditto HTTP push connection is configured in ditto, which sends events from topic **_/_/things/twin/events?filter=eq(topic:action,'modified'** to a monster mock endpoint, which replies with HTTP status code 204.

Also, there is a special scenario called Warmup, which is used to warmup the system, by executing a single read request for each thing, which will cause them to get cached.

# Getting started:

## ## K6 is configurable via environment variables and the following must be set, in order to run the test:

## K6 test related

| Name                          | Description                                                                                                                                                 |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| SETUP_TIMEOUT                 | Specify how long the k6 setup() function is allow to run before it's terminated                                                                             |
| TEARDOWN_TIMEOUT              | Specify how long the k6 teardown() function is allowed to run before it's terminated                                                                        |
| THINGS_NAMESPACE              | remove?Namepsace to use for created ditto things                                                                                                            |
| THINGS_ID_TEMPLATE            | remove?Things id template, f.e. 'my-thing-{0}'                                                                                                              |
| THINGS_START_INDEX            | remove?Things start index, used in template, f.e.  if start index is 1, created things have name of: 'my-thing-1', 'my-thing-2', etc.                       |
| CREATE_THINGS_BATCH_SIZE      | Creating things is done via implicitThingCreationMapper via kafka connection. The kafka messages are sent in batches and this variable sets the batch size. |
| CREATE_THINGS                 | If the test should create things, before executing the scenarios (0/1)                                                                                      |
| DELETE_THINGS                 | If the test should delete the things, after executing the scenarios (0/1)                                                                                   |
| KAFKA_PRODUCER_LOGGER_ENABLED | K6 kafka producer logger enabled (0/1)                                                                                                                      |
| KAFKA_CONSUMER_LOGGER_ENABLED | K6 kafka consumer logger enabled (0/1)                                                                                                                      |
| CREATE_DITTO_CONNECTIONS      | If the test should create the needed for scenarios ditto connections, before executing the scenarios                                                        |
| DELETE_DITTO_CONNECTIONS      | If the test should delete the needed for scenarios ditto connections, after executing the scenarios                                                         |
| SCENARIOS_TO_RUN              | Array of scenarios names that should run, available options is: WARMUP, DEVICE_LIVE_MESSAGES, SERACH_THINGS, READ_THINGS, MODIFY_THINGS                     |
| LOG_REMAINING                 | Log the remaining things that need to be created. Useful for debugging purposes                                                                             |
| BATCH_SIZE                    | Max number of simultaneous connections of a k6 http.batch() call, which is used for warming up things                                                       |

## Ditto related

| Name                            | Description                        |
| ------------------------------- | ---------------------------------- |
| DITO_API_URI                    | Ditto api url                      |
| DITTO_AUTH_CONTEXT_HEADER       | Authorization context header name  |
| DITTO_AUTH_CONTEXT_HEADER_VALUE | Authorization context header value |
| DEVICE_FEATURE_NAME             | remove?                            |
| DEVICE_FEATURE_PROPERTY         | remove?                            |
| DEVICE_ID_HEADER                | remove?                            |
| CREATE_THING_HEADER             | remove?                            |
| DITTO_MESSAGE_HEADER            | remove?                            |

## Kafka related

| Name                    | Description                      |
| ----------------------- | -------------------------------- |
| KAFKA_BOOTSTRAP_SERVERS | Array of kafka bootstrap servers |

## Scenarios related

###### WARMUP

| Name                | Description                                                                                  |
| ------------------- | -------------------------------------------------------------------------------------------- |
| WARMUP_MAX_DURATION | The maximum duration of warmup scenario. After, the scenario will be forcefully stopped      |
| WARMUP_START_TIME   | Time offset since the start of the test, at which point this scenario should begin execution |
| WARMUP_VUS          | An integer value specifying the number of VUs to run concurrently                            |

###### Every other scenario has the same config variables, created by suffixing the variable name with the name of the scenario, f.e. SEARCH_THINGS_DURATION

| Name               | Description                                                                                        |
| ------------------ | -------------------------------------------------------------------------------------------------- |
| _DURATION          | Total scenario duration.                                                                           |
| _PER_SECOND        | Number of requests to execute per second. For kafka scenarios number of kafka messages per second. |
| _PRE_ALLOCATED_VUS | Number of VUs to pre-allocate before scenario start to preserve runtime resources.                 |
| __MAX_VUS          | Maximum number of VUs to allow during the scenario run.                                            |
| __START_TIME       | Time offset since the start of the test, at which point this scenario should begin execution.      |

###### Ditto kafka connections

| Name                                        | Description                                                                   |
| ------------------------------------------- | ----------------------------------------------------------------------------- |
| KAFKA_CONNECTION_QOS                        | Ditto kafka connection qos value (0/1)                                        |
| KAFKA_CONNECTION_CUSTOM_ACK                 | Ditto kafka connection custom acknowledge name                                |
| KAFKA_SOURCE_CONNECTION_CLIENT_COUNT        | Ditto source (consumer) kafka connection client count                         |
| KAFKA_TARGET_CONNECTION_CLIENT_COUNT        | Ditto target (producer) kafka connection client count                         |
| CREATE_UPDATE_THING_SOURCE_TOPIC            | Kafka topic for sending create/update messages to ditto source connection     |
| CREATE_UPDATE_THING_SOURCE_TOPIC_PARTITIONS | Number of partitions for the create/update kafka topic                        |
| CREATE_UPDATE_THING_REPLY_TOPIC             | Kafka topic for ditto target connection, replying with 'thing created' events |
| CREATE_UPDATE_THING_REPLY_TOPIC_PARTITIONS  | Number of partitions for the create/update reply kafka topic                  |
| CREATE_UPDATE_THING_CONSUMER_GROUP_ID       | K6 kafka consumer group id                                                    |

###### Ditto HTTP Push connection

| Name                               | Description                                                                                    |
| ---------------------------------- | ---------------------------------------------------------------------------------------------- |
| HTTP_PUSH_CONNECTION_CLIENT_COUNT  | Ditto HTTP push connection client count                                                        |
| HTTP_PUSH_CONNECTION_PARALLELISM   | Ditto HTTP push connection parallelism                                                         |
| PUSH_ENDPOINT_URI                  | Ditto HTTP push connection endpoint uri                                                        |
| PUSH_ENDPOINT_EVENTS_MODIFIED_PATH | Ditto HTTP push connection target address for thing modified event                             |
| PUSH_ENDPOINT_LIVE_MESSAGE_PATH    | Ditto HTTP push connection target address for things live messages events                      |

## Test lifecycle

The test consists of 4 lifecycle stages: **init**, **setup**, **VU code** and **teardown**

**Init**

1. Parses all environment variables

2. Creates global kafka producer

**Setup**

1. Creates kafka topics if **CREATE_DITTO_CONNECTIONS** env var is **1**

2. Creates ditto connections ( ditto kafka, ditto http push connections ) if **CREATE_DITTO_CONNECTIONS** env var is **1**

3. Creates things, if **CREATE_THINGS** env var is **1**

**VU code** - the stage at which the scenarios get executed

**Teardown**

1. Deletes things, if **DELETE_THINGS** env var is **1**

2. Deletes ditto connections ( ditto kafka, ditto http push connections ) if **DELETE_DITTO_CONNECTIONS** env var is **1

3. Deletes kafka topics if **DELETE_DITTO_CONNECTIONS** env var is **1

## 

## Running the test

###### Running the test locally

Prerequisites:

- Running ditto

- Running kafka cluster with topic deletion enabled

- Running Monster mock instance ( config for mmock is inside the mmock directory )

- Having the xk6 kafka extension binary

First export all the environment variables, needed for the test:

```bash
set -a
. test.env
set +a
```

  Then, to run the test:

```bash
{xk6-kakfa-bin} run test/k6-test.js
```

###### Running the test inside kubernetes cluster

Prerequisites:

- Running kubernetes cluster

- Running kafka cluster with topic deletion disabled

- Running ditto inside the cluster, using the ditto helm chart https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm/ditto

- Deploy the k6 operator [GitHub - grafana/k6-operator: An operator for running distributed k6 tests.](https://github.com/grafana/k6-operator)[GitHub - grafana/k6-operator: An operator for running distributed k6 tests.](https://github.com/grafana/k6-operator)

- Create config map for mmock from the config files inside **mmock** directory:
  
  ```bash
  kubectl create configmap mmock-config --from-file mmock/
  ```

- Running Monster mock instance inside the cluster (kubernetes resource inside kubernetes directory)



Needed kubernetes resources lie inside the kubernetes directory.

- k6-test-configmap-cr.yaml - custom k6 resource, includes all env variables needed for the test, that are inside test.env file

- mmock-pvc.yaml - Persistent volme claim for monster mock, use to copy the mmock configuration to the created PV, in order to mount it inside the mmock instance.

- mmock.yaml - Pod definition for monster mock

K6 custom resource gets the source code for the test from a config map, that must be created:

```bash
 kubectl create configmap k6-test --from-file test/
```

K6 custom resource reads env variables from config map that must be created:

```bash
kubectl create configmap k6-ditto-benchmark --from-env-file test-cluster.env
```

After all is set, create the k6 custom resource for the test:

```bash
k create -f k6-ditto-benchmark-test.yaml
```

Logs of the k6 test can be inspected from the pod **k6-ditto-benchmark-test-1-xxxx**
