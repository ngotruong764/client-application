# client-application

## Run application:
1. Compile  all Java files  
``javac -cp ".:lib/common-service-1.0-SNAPSHOT.jar" $(find src/main/java -name "*.java")``
2. Run the application  
``java -cp ".:lib/common-service-1.0-SNAPSHOT.jar:src/main/java" ClientApplicationMain``