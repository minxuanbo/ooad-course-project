ARG JAVA_BUILD_IMAGE=ooad-java-build:clonefactory-0.0.2
FROM ${JAVA_BUILD_IMAGE} AS build
WORKDIR /build
COPY backend/pom.xml pom.xml
COPY backend/src/main src/main
RUN mvn -B -ntp -s /opt/maven-settings.xml -Dmaven.test.skip=true package

FROM swr.cn-north-4.myhuaweicloud.com/oomall-javaee/openjdk@sha256:cab51c50750848d1f3da11f57d9994be445db7bf06d2916408fac65ad389469b
WORKDIR /app
RUN addgroup -g 10001 rbac && adduser -D -u 10001 -G rbac rbac
COPY --from=build /build/target/rbac0-0.1.0.jar /app/app.jar
USER 10001:10001
ENV APP_BACKEND_STRATEGY=ddd
EXPOSE 8081 8082
ENTRYPOINT ["java"]
CMD ["-jar", "/app/app.jar"]
