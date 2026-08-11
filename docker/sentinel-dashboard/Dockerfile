FROM curlimages/curl:8.11.1 AS downloader

RUN curl --fail --location \
    https://github.com/alibaba/Sentinel/releases/download/1.8.9/sentinel-dashboard-1.8.9.jar \
    --output /tmp/sentinel-dashboard.jar

FROM eclipse-temurin:17-jre

COPY --from=downloader /tmp/sentinel-dashboard.jar /opt/sentinel-dashboard.jar

EXPOSE 8858

ENTRYPOINT ["java", "-Dserver.port=8858", "-Dcsp.sentinel.dashboard.server=localhost:8858", "-Dproject.name=sentinel-dashboard", "-jar", "/opt/sentinel-dashboard.jar"]
