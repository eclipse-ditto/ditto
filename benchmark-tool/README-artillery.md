# Eclipse Ditto Load Tool

Based on Artillery framework github.com/artilleryio/artillery/ (Licensed under MPL 2.0) benchmark tests for Eclipse Ditto.

## Features

- **TypeScript**: Full TypeScript implementation with type safety and compile-time checking
- **Multi-Channel Architecture**: Support for HTTP, WebSocket (Twin/Live), and Kafka channels
- **Eclipse Ditto JavaScript Client**: Uses the official Ditto Node.js client for HTTP and WebSocket operations
- **KafkaJS**: Kafka client for Kafka operations
- **Scenario-based Testing**: Run individual or multiple scenarios (warmup, readThings, searchThings, modifyThings, deviceLiveMessages)

## Prerequisites

- Node.js 18+ and npm
- Running Eclipse Ditto instance
- Running Kafka cluster with topic deletion enabled
- Running Monster Mock (MMock) instance for HTTP push endpoints(config included in kubernetes/ and mmock/ dir)

## Installation

```bash
cd artillery-test/
npm install
npm run build
```

## Configuration

Configuration is done via a YAML file (`config.yml`) with optional environment variable overrides.

### YAML Configuration (Recommended)

Create or edit the default provided `config.yml` in the artillery-test directory.

### Custom Config Path

By default, the tool looks for `./config.yml`. Use the `CONFIG_PATH` environment variable to specify a different path:

```bash
CONFIG_PATH=/path/to/my-config.yml npm run test
```

### Environment Variable Overrides

Environment variables can override any YAML config value.

```bash
# Load base config, then override specific values
export THINGS_COUNT=5000
export ARTILLERY_ARRIVAL_RATE=1000
# export ...
npm run test
```

**Key Environment Variables:**

| Variable | Description |
|----------|-------------|
| `CONFIG_PATH` | Path to YAML config file (default: `./config.yml`) |
| `DITTO_BASE_URI` | Ditto API base URL |
| `THINGS_COUNT` | Number of things to create/use |
| `CREATE_THINGS` | Set to "1" to create things before test |
| `DELETE_THINGS` | Set to "1" to delete things after test |
| `CREATE_DITTO_CONNECTIONS` | Set to "1" to create Ditto connections |
| `DELETE_DITTO_CONNECTIONS` | Set to "1" to delete connections after test |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses |
| `ARTILLERY_DURATION` | Test phase duration (e.g., "45s") |
| `ARTILLERY_ARRIVAL_RATE` | Requests per second |
| `RUN_WARMUP` | Set to "1" to enable warmup phase |
| `DEBUG_LOGGING` | Set to "1" to enable debug logging |
| `CLEANUP_ONLY` | Set to "1" to only run cleanup (no test scenarios) |

### Scenario Configuration

Each scenario can be enabled/disabled and configured with channel weights:

```yaml
scenarios:
  readThings:
    enabled: true      # Enable this scenario
    channels:
      HTTP: 1          # Weight for HTTP channel
  modifyThings:
    enabled: true
    channels:
      HTTP: 1          # All three channels enabled
      WEBSOCKET_TWIN: 1
      KAFKA: 1
  searchThings:
    enabled: false     # Disabled scenario
    channels: {}
```

**Channel weight** determines the ratio of requests sent via each channel. A weight of 0 disables that channel.

### Channel Capabilities

**Available Channels:**
- `HTTP` - Standard HTTP REST API (supports all operations)
- `WEBSOCKET_TWIN` - WebSocket twin channel (currently supports: modifyThing only)
- `WEBSOCKET_LIVE` - WebSocket live channel (currently supports: sendLiveMessage)
- `KAFKA` - Kafka messaging (supports: modifyThing only)

**Channel Capabilities Matrix:**

| Operation | HTTP | WEBSOCKET_TWIN | WEBSOCKET_LIVE | KAFKA |
|-----------|------|----------------|----------------|-------|
| getThing | Yes | No | No | No |
| getThingsBatch | Yes | No | No | No |
| searchThings | Yes | No | No | No |
| modifyThing | Yes | Yes | No | Yes |
| sendLiveMessage | Yes | No | Yes | No |

**Automatic Validation:**

The Artillery configuration generator automatically validates scenario+channel combinations and skips incompatible pairs. For example:
- `READ_THINGS_WEBSOCKET_TWIN` is skipped (Twin channel only supports modifyThing)
- `SEARCH_THINGS_WEBSOCKET_TWIN` is skipped (WebSocket doesn't support search)
- `READ_THINGS_KAFKA` is skipped (Kafka doesn't support read operations)

## Architecture

**Usage Pattern:**

```typescript
import { HttpChannel } from '../channels/http-channel';
import { WebSocketChannel } from '../channels/websocket-channel';

// Simple scenarios hardcode HTTP
const channelClient = new HttpChannel();
await channelClient.getThing({ thingId: 'org.eclipse.ditto:thing-1' });

// Multi-channel scenarios use switch routing
let channelClient: HttpChannel | WebSocketChannel;

switch (channelType) {
    case 'HTTP':
        channelClient = new HttpChannel();
        break;
    case 'WEBSOCKET_LIVE':
        const wsChannel = new WebSocketChannel();
        wsChannel.getLiveClient();
        channelClient = wsChannel;
        break;
    default:
        throw new Error(`Unsupported channel: ${channelType}`);
}

await channelClient.sendLiveMessage({ thingId, subject, payload });
```

### Artillery Lifecycle

1. **Before Hook** (runs once, not in VU context):
   - `beforeTest()`: Initialize Ditto client, create connections and things
   - `runWarmupIfEnabled()`: Pre-cache all things by reading them once

2. **VU Execution** (each VU runs scenarios):
   - Multiple VUs can spawn based on Artillery's `arrivalRate` configuration
   - Each VU executes enabled scenarios

3. **After Hook** (runs once after all VUs complete):
   - `afterTest()`: Clean up things, connections, and Kafka topics

## Running the load test

```bash
# Run all configured scenarios
npm run test

# Run specific scenarios
SCENARIOS_TO_RUN="readThings,searchThings" npm run test
```

## Cleaning Up Residual Test Data

If a test run is interrupted or fails, residual data (things, connections, Kafka topics) may remain. Use cleanup-only mode to remove this data.

### Quick Cleanup

```bash
# Clean up everything from the last test run
CLEANUP_ONLY=1 npm run test
```

### Selective Cleanup

```bash
# Delete only things (not connections or topics)
CLEANUP_ONLY=1 DELETE_THINGS=1 DELETE_DITTO_CONNECTIONS=0 npm run test

# Delete only connections
CLEANUP_ONLY=1 DELETE_THINGS=0 DELETE_DITTO_CONNECTIONS=1 npm run test
```

### Cleaning Up Specific Connections

If you need to delete specific pre-existing connections (e.g., from a previous test with different IDs):

```bash
CLEANUP_ONLY=1 \
  DELETE_DITTO_CONNECTIONS=1 \
  CREATE_DITTO_CONNECTIONS=0 \
  KAFKA_SOURCE_CONNECTION_ID="<uuid>" \
  KAFKA_TARGET_CONNECTION_ID="<uuid>" \
  HTTP_PUSH_CONNECTION_ID="<uuid>" \
  npm run test
```

### Automatic Cleanup Instructions

If cleanup fails during a test run, the tool will print manual cleanup instructions with the exact configuration needed to retry cleanup.

## Scenarios description

### WARMUP
Pre-caches all things by reading each one once in batches. Runs in the `before` hook.

**Configuration:**
- `THINGS_WARMUP_MAX_DURATION`: Maximum duration (e.g., "60s")
- `THINGS_WARMUP_BATCH_SIZE`: Batch size for warmup

### READ_THINGS
Reads random things via HTTP GET.

### SEARCH_THINGS
Searches for random things via Ditto search API.

### MODIFY_THINGS
Modifies random things via configured channel (HTTP, WebSocket(twin), or Kafka).

### DEVICE_LIVE_MESSAGES
Sends live messages to random things via HTTP or WebSocket(live).

## File Structure

### Core TypeScript Files
- `artillery.ts`: Dynamic Artillery configuration generator with validation
- `processor.ts`: Main processor with lifecycle hooks and scenario functions

### Channel Implementations (`channels/`)
- `http-channel.ts`: HTTP channel
- `websocket-channel.ts`: WebSocket channel
- `kafka-channel.ts`: Kafka channel

### Scenario Files (`scenarios/`)
- `warmup-scenario.ts`: WARMUP scenario
- `read-things-scenario.ts`: READ_THINGS scenario
- `search-things-scenario.ts`: SEARCH_THINGS scenario
- `modify-things-scenario.ts`: MODIFY_THINGS scenario
- `device-live-messages-scenario.ts`: DEVICE_LIVE_MESSAGES scenario

## Adding a New Scenario

To add a new scenario (e.g., `deleteThing`), follow these steps:

### 1. Define the scenario name (`config/scenarios-config.ts`)

```typescript
// Add constant
export const DELETE_THING = 'deleteThing';

// Add to ScenarioName type
export type ScenarioName =
  | typeof READ_THINGS
  | typeof DELETE_THING  // Add here
  | ...;

// Add default config in getDefaultScenariosConfig()
{
    name: DELETE_THING,
    enabled: false,
    channels: [],
    timeout: DEFAULT_SCENARIO_TIMEOUT
}
```

### 2. Create scenario file (`scenarios/delete-thing-scenario.ts`)

```typescript
import { Scenario } from '../interfaces';
import { getConfig, OPERATION_DELETE_THING } from '../common';
import { executeWithMetrics } from './scenario-utils';
import { HttpChannel } from '../channels/http-channel';

export const deleteThingScenario: Scenario = {
    async execute(context: any, events: any): Promise<void> {
        const channelClient = new HttpChannel();
        const thingId = getConfig().getRandomThingId();

        await executeWithMetrics(
            () => channelClient.deleteThing({ thingId }),
            OPERATION_DELETE_THING,
            events,
            context
        );
    }
};
```

### 3. Add operation constant and channel support (`common.ts`)

```typescript
// Add operation name for metrics
export const OPERATION_DELETE_THING = 'deleteThing';

// Add supported channels
export const SCENARIO_SUPPORTED_CHANNELS: Record<ScenarioName, ChannelName[]> = {
    ...
    [DELETE_THING]: [CHANNEL_HTTP],  // Add supported channels
};
```

### 4. Register in processor (`processor.ts`)

```typescript
// Import
import { deleteThingScenario } from './scenarios/delete-thing-scenario';

// Add wrapper function
export async function runDeleteThing(context: any, events: any): Promise<void> {
    await runScenario(context, events, deleteThingScenario);
}
```

### 5. Map to Artillery config (`artillery.ts`)

```typescript
const SCENARIO_FUNCTION_NAMES: Record<ScenarioName, string> = {
    ...
    [DELETE_THING]: 'runDeleteThing',
};
```

### 6. Configure in `config.yml`

```yaml
scenarios:
  - name: deleteThing
    enabled: true
    channels:
      - name: http
        weight: 1
```

## Troubleshooting

### Connection Timeout Errors
Increase `CONNECTION_OPEN_MAX_RETRIES` in test.env or check that Ditto and Kafka are accessible.

### Turn debug log level
Set `DEBUG_LOGGING=1`

### Kafka Consumer Timeout
Increase `CREATE_UPDATE_THING_CONSUMER_MAX_WAIT_DURATION` in test.env.

## Examples

### Full Test with Thing Creation, configure the correct endpoints for ditto in config.yml and run
```bash
npm run test
```

## Kubernetes Deployment

### Prerequisites

- Kubernetes cluster with access to the local registry
- [Optional] StorageClass with dynamic provisioning (for PVC to save test results)

### Deployment

```bash
kubectl create configmap -n mmock mmock-config --from-file ../mmock/
kubectl create -f ../kubernetes/mmock.yaml

cd artillery-test/

# Create config (optional - override the default config)
kubectl create configmap artillery-test-config --from-file=config-kubernetes.yml

# Create environment overrides (optional)
kubectl create configmap artillery-test-env --from-env-file=test.env

# Create secrets for sensitive data (optional)
kubectl create secret generic artillery-test-secrets \
  --from-literal=DITTO_DEVOPS_USER=devops \
  --from-literal=DITTO_DEVOPS_PASSWORD=secret

# Deploy the job
kubectl create -f ../kubernetes/artillery-test.yaml

# Watch the test progress
kubectl logs -f job/artillery-test

# Check job status
kubectl get job artillery-test
```

### Access Test Results
```bash
kubectl create -f ../kubernetes/artillery-results.yaml
kubectl cp artillery-results:/results/results.json ./results.json
kubectl delete pod artillery-results

# Generate report from results
npm run report
```

### Cleanup

```bash
kubectl delete job artillery-test
kubectl delete configmap artillery-test-config artillery-test-env --ignore-not-found
kubectl delete secret artillery-test-secrets --ignore-not-found
kubectl delete pvc artillery-test-results
```

### Customizing the Kubernetes Job

Edit `kubernetes/artillery-test.yaml` to:

- Adjust resources: Modify CPU/memory requests and limits
- Add node selectors or tolerations for specific node placement
- Change the config path via `CONFIG_PATH` environment variable

## Further Reading

- [Artillery Documentation](https://www.artillery.io/docs)
- [Eclipse Ditto Documentation](https://eclipse.dev/ditto/)
- [Ditto JavaScript Client](https://github.com/eclipse-ditto/ditto-clients/tree/master/javascript)
- [KafkaJS Documentation](https://kafka.js.org/)
