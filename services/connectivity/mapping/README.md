Contains tests and benchmarks for JavaScript mapper implementation.

# JMH tutorials

http://tutorials.jenkov.com/java-performance/jmh.html

http://java-performance.info/jmh/

# Execute JMH benchmarks

Simply execute:
```bash
mvn clean package

java -jar target/ditto-services-connectivity-mapping-<version>-benchmark.jar

java -jar target/ditto-services-connectivity-mapping-<version>-benchmark.jar -rf csv
```

Use the last one in order to generate a .csv file with which you can work in Excel.
