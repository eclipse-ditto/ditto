# Common Troubleshooting

## Build Issues

### "SNAPSHOT dependencies not allowed"

**Problem**: Build fails with message about SNAPSHOT dependencies.

**Solution**:
- Check if `performRelease` profile is accidentally active
- Remove the profile: `mvn clean install` (without `-P performRelease`)
- Ensure you're building a SNAPSHOT version, not a release

### License Header Check Fails

**Problem**: `mvn license:check` fails.

**Solution**:
```bash
# Automatically add/update license headers
mvn license:format
```

### Build Fails After Pulling Latest Changes

**Problem**: Build errors after `git pull`.

**Solution**:
```bash
# Clean and rebuild everything
mvn clean install -DskipTests
```

## Test Issues

### Tests Fail with MongoDB Connection Errors

**Problem**: Tests fail trying to connect to MongoDB.

**Solution**:
- Ensure Docker daemon is running
- Tests use **embedded MongoDB** - no separate instance needed
- If still failing, check Docker Desktop has enough resources

### Specific Test Keeps Failing

**Problem**: One test consistently fails.

**Solution**:
```bash
# Run just that test with verbose output
mvn test -Dtest=YourTest -X

# Check if test needs specific order or setup
# Look for @BeforeAll, @BeforeEach requirements
```

### Integration Tests Timeout

**Problem**: Integration tests hang or timeout.

**Solution**:
- Increase timeout in test: `@Timeout(value = 30, unit = TimeUnit.SECONDS)`
- Check if actor system is properly shut down in `@AfterEach`
- Look for deadlocks in actor message flow

## Docker / Deployment Issues

### Docker Compose Fails with "Insufficient Resources"

**Problem**: Services fail to start or crash immediately.

**Solution**:
- Ditto requires **minimum 2 CPU cores** and **4GB RAM** for Docker
- Increase Docker resource limits in Docker Desktop settings
- Check with: `docker stats` to see resource usage

### Service Won't Start - Port Already in Use

**Problem**: `bind: address already in use` error.

**Solution**:
```bash
# Find what's using the port (e.g., 8080)
lsof -i :8080

# Stop conflicting service or change port in docker-compose.yml
```

### Cannot Connect to Ditto After Starting

**Problem**: Services are running but API doesn't respond.

**Solution**:
- Wait 30-60 seconds for all services to initialize
- Check logs: `docker-compose logs -f`
- Verify MongoDB is ready: `docker-compose logs mongodb`
- Try accessing: http://localhost:8080

### Local Images Not Used

**Problem**: Docker Compose uses Docker Hub images instead of local builds.

**Solution**:
```bash
# Ensure .env file exists with SNAPSHOT version
cd deployment/docker/
cp dev.env .env

# Rebuild images
cd ../..
./build-images.sh

# Restart compose
cd deployment/docker/
docker-compose down
docker-compose up -d
```

## Actor / Runtime Issues

### Actor Not Receiving Messages

**Problem**: Actor exists but messages aren't being processed.

**Solution**:
- Check shard region is started in service's root actor
- Verify cluster role configuration in application.conf
- Check entity ID extraction logic in shard region
- Use Pekko logging to trace message flow

### Memory Issues / OOM Errors

**Problem**: OutOfMemoryError or high memory usage.

**Solution**:
- Check for policy evaluator setting: consider memory-optimized version
- Review snapshot configuration - too frequent snapshots can cause issues
- Look for actor leaks - ensure actors are stopped when done
- Increase JVM heap: `-Xmx2G` in Docker or local runs

### Cluster Not Forming

**Problem**: Services start but don't join cluster.

**Solution**:
- Check network connectivity between services
- Verify seed nodes are configured correctly
- Check cluster roles are properly set
- Look for split-brain scenarios in logs

## Development Issues

### IDE Doesn't Recognize Classes

**Problem**: IntelliJ or Eclipse shows errors but Maven builds fine.

**Solution**:
- Reimport Maven project
- Invalidate caches and restart IDE
- Ensure JDK 21 is configured
- Run `mvn clean install` to generate any generated sources

### Code Style Check Fails

**Problem**: PR rejected due to style violations.

**Solution**:
- Apply Google Java Style Guide formatter
- Check `.editorconfig` is respected by IDE
- Line length must be 120 characters, not 100
- Ensure 4 spaces indentation (not tabs)

## Getting Help

If the above doesn't solve your issue:

1. **Search GitHub Issues**: https://github.com/eclipse-ditto/ditto/issues
2. **Ask on Gitter**: https://gitter.im/eclipse/ditto
3. **Create a new issue** with:
   - Ditto version
   - Steps to reproduce
   - Expected vs actual behavior
   - Relevant logs
   - Environment details (OS, Java version, Docker version)
