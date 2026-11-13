FROM swr.cn-north-4.myhuaweicloud.com/oomall-javaee/openjdk@sha256:cab51c50750848d1f3da11f57d9994be445db7bf06d2916408fac65ad389469b AS build
WORKDIR /build
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
RUN wget -q https://repo.huaweicloud.com/apache/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz \
    && tar -xzf apache-maven-3.9.9-bin.tar.gz -C /opt
COPY deploy/maven-settings.xml /opt/maven-settings.xml
# Install the teacher's real annotation processor at the reviewed source revision.
RUN apk add --no-cache git \
    && git init /tmp/course-clonefactory \
    && git -C /tmp/course-clonefactory remote add origin https://gitee.com/mingqcn/spring-fundamentals.git \
    && git -C /tmp/course-clonefactory fetch --depth 1 origin 237f49c52bdc72da30383571bb2f985ac54a514a \
    && git -C /tmp/course-clonefactory checkout --detach FETCH_HEAD \
    && cd /tmp/course-clonefactory/clonefactory \
    && /opt/apache-maven-3.9.9/bin/mvn -B -ntp -s /opt/maven-settings.xml -Dmaven.compiler.proc=full clean org.apache.maven.plugins:maven-compiler-plugin:3.14.0:compile jar:jar install:install \
    && rm -rf /tmp/course-clonefactory
COPY pom.xml .
COPY src src
RUN /opt/apache-maven-3.9.9/bin/mvn -B -s /opt/maven-settings.xml -DskipTests package

FROM swr.cn-north-4.myhuaweicloud.com/oomall-javaee/openjdk@sha256:cab51c50750848d1f3da11f57d9994be445db7bf06d2916408fac65ad389469b
WORKDIR /app
RUN addgroup -g 10001 rbac && adduser -D -u 10001 -G rbac rbac
COPY --from=build /build/target/rbac0-0.1.0.jar /app/app.jar
USER 10001:10001
ENV PORT=8081
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
