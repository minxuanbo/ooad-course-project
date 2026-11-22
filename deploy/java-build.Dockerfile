FROM swr.cn-north-4.myhuaweicloud.com/oomall-javaee/openjdk@sha256:cab51c50750848d1f3da11f57d9994be445db7bf06d2916408fac65ad389469b
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
ENV PATH=/opt/apache-maven-3.9.9/bin:$PATH
ENV MAVEN_OPTS="-Xmx384m"
COPY deploy/maven-settings.xml /opt/maven-settings.xml
RUN apk add --no-cache git curl \
    && curl -fL --connect-timeout 15 --max-time 180 --retry 3 https://repo.huaweicloud.com/apache/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz -o /tmp/maven.tgz \
    && tar -xzf /tmp/maven.tgz -C /opt \
    && rm /tmp/maven.tgz \
    && git init /tmp/clonefactory \
    && git -C /tmp/clonefactory remote add origin https://gitee.com/mingqcn/spring-fundamentals.git \
    && git -C /tmp/clonefactory fetch --depth 1 origin 40583ff94764d2e758bcb13be17f5c05ea4d6352 \
    && git -C /tmp/clonefactory checkout --detach FETCH_HEAD \
    && cd /tmp/clonefactory/clonefactory \
    && test "$(tr -d '\r' < src/main/java/cn/edu/xmu/clonefactory/CopyProcessor.java | sha256sum | cut -d ' ' -f 1)" = 60ee8327a248a96e1a9541cba4b9005f7f411d03c4f6f3af370c7df44285e831 \
    && mvn -B -ntp -s /opt/maven-settings.xml -Dmaven.compiler.proc=full clean install \
    && rm -rf /tmp/clonefactory
