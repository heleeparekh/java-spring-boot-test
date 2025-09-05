# java-spring-boot-test

Thread




Helee Parekh
  33 minutes ago
Hello,
I’m working on a Java Spring Boot based generative AI application. The LLM we’re using is served through our own API, and I’d like to log the spans into Arize Phoenix. Is there any documentation or examples on how to set this up for Java? Any pointers would be super helpful.
Thanks!
3 replies

RunLLM
APP  33 minutes ago
Yes, you can instrument your Java Spring Boot app to send traces to Arize Phoenix using OpenTelemetry and OpenInference. There are example projects and documentation showing how to configure the OpenTelemetry SDK, set up exporters, and send traces to Phoenix. See the Spring AI Java example and the OpenInference Java SDK guide for step-by-step setup.
You’ll need to configure an OTLP exporter in your Java app to point to your Phoenix instance, and instrument your code to create spans for LLM calls. Would you like more detail or a code snippet for this setup?
Sources:
- Spring AI Java example
- OpenInference Java SDK guide
See less
:bulb: Hint: Mention @RunLLM in the thread for followups.


Xander Song
  30 minutes ago
Hey @Helee Parekh, we may be missing documentation, but we do have a Java libraries here
https://central.sonatype.com/artifact/com.arize/openinference-instrumentation-springAI
https://central.sonatype.com/artifact/com.arize/openinference-semantic-conventions
Maven CentralMaven Central
Maven Central: com.arize:openinference-instrumentation-springAI
Discover openinference-instrumentation-springAI in the com.arize namespace. Explore metadata, contributors, the Maven POM file, and more.
Maven CentralMaven Central
Maven Central: com.arize:openinference-semantic-conventions
Discover openinference-semantic-conventions in the com.arize namespace. Explore metadata, contributors, the Maven POM file, and more.


Xander Song
  29 minutes ago
cc @Jonathan Beltran
