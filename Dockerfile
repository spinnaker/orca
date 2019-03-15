FROM openjdk:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN GRADLE_USER_HOME=cache ./gradlew -I gradle/init-publish.gradle buildDeb -x test && \
  dpkg -i ./orca-web/build/distributions/*.deb && \
  cd .. && \
  rm -rf workdir

CMD ["/opt/orca/bin/orca"]
