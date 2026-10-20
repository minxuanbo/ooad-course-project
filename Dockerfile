FROM swr.cn-north-4.myhuaweicloud.com/oomall-javaee/openjdk@sha256:cab51c50750848d1f3da11f57d9994be445db7bf06d2916408fac65ad389469b AS build
WORKDIR /build
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
RUN wget -q https://repo.huaweicloud.com/apache/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz \
    && tar -xzf apache-maven-3.9.9-bin.tar.gz -C /opt
COPY deploy/maven-settings.xml /opt/maven-settings.xml
COPY pom.xml .
COPY src src
RUN /opt/apache-maven-3.9.9/bin/mvn -B -s /opt/maven-settings.xml -DskipTests package

FROM swr.cn-north-4.myhuaweicloud.com/oomall-javaee/openjdk@sha256:cab51c50750848d1f3da11f57d9994be445db7bf06d2916408fac65ad389469b
WORKDIR /app
RUN addgroup -g 10001 rbac && adduser -D -u 10001 -G rbac rbac
COPY --from=build /build/target/rbac0-0.1.0.jar /app/app.jar
COPY 市政公司员工信息表.xlsx 市政公司部门信息表.xlsx 市政公司应用软件功能权限清单.xlsx /app/source/
USER 10001:10001
ENV RBAC_SOURCE_DIR=/app/source PORT=8081
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
