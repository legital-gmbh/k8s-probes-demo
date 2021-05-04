FROM openjdk:11
RUN adduser --system --group spring
USER spring:spring
ARG UNPACKED=target/deployment
COPY ${UNPACKED}/BOOT-INF/lib /app/lib
COPY ${UNPACKED}/META-INF /app/META-INF
COPY ${UNPACKED}/BOOT-INF/classes /app
EXPOSE 8080
ENTRYPOINT ["java","-cp","app:app/lib/*","org.legital.k8sprobesdemo.K8sProbesDemoApplicationKt"]
