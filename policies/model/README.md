Contains tests and benchmarks for different PolicyEnforcers implementations.

# JMH tutorials

http://tutorials.jenkov.com/java-performance/jmh.html

http://java-performance.info/jmh/

# Execute JMH benchmarks

Simply execute:
```bash
mvn clean package -Pbuild-benchmark-assembly

java -jar target/ditto-policies-model-<version>-benchmark.jar

java -jar target/ditto-policies-model-<version>-benchmark.jar -rf csv
```

Use the last one in order to generate a .csv file with which you can work in Excel.
