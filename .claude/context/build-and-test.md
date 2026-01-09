# Build & Test Commands

## Building

```bash
# Full build with tests
mvn clean install

# Build without tests (faster)
mvn clean install -DskipTests

# Build Docker images locally
./build-images.sh

# Build with proxy (if needed)
./build-images.sh -p 172.17.0.1:3128
```

## Running Tests

```bash
# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl things/service

# Run specific test class
mvn test -Dtest=ThingPersistenceActorTest

# Run specific test method
mvn test -Dtest=ThingPersistenceActorTest#testCreateThing

# Run integration tests
mvn verify

# Run specific integration test
mvn verify -Dit.test=ConnectionPersistenceActorIT

# Skip unit tests, run only integration tests
mvn verify -DskipTests -DskipITs=false
```

## Local Development Environment

```bash
# Start Ditto with latest Docker Hub images
cd deployment/docker/
docker-compose up -d

# Start Ditto with local snapshot images (after building)
cd deployment/docker/
cp dev.env .env
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

## Code Quality

```bash
# Check license headers
mvn license:check

# Add/update license headers
mvn license:format

# Inspect deprecated method usage
mvn clean install -P inspect-deprecations

# Generate test coverage report (jacoco)
mvn clean verify
# Reports in: target/site/jacoco/index.html
```

## UI Development

The Explorer UI is a TypeScript/SCSS application in `/ui/`:

```bash
cd ui/

# Install dependencies (if package.json changes)
npm install

# Build UI
npm run build

# Output in ui/dist/
```
