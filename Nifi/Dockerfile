FROM apache/nifi:latest
USER root

ENV NIFI_LIB_DIR=/opt/nifi/nifi-current/lib
ENV NIFI_VERSION="2.0.0"

RUN curl -L -o ${NIFI_LIB_DIR}/nifi-parquet-nar-${NIFI_VERSION}.nar \
    "https://repo1.maven.org/maven2/org/apache/nifi/nifi-parquet-nar/${NIFI_VERSION}/nifi-parquet-nar-${NIFI_VERSION}.nar" && \
    curl -L -o ${NIFI_LIB_DIR}/nifi-hadoop-libraries-nar-${NIFI_VERSION}.nar \
    "https://repo1.maven.org/maven2/org/apache/nifi/nifi-hadoop-libraries-nar/${NIFI_VERSION}/nifi-hadoop-libraries-nar-${NIFI_VERSION}.nar"

RUN chown nifi:nifi ${NIFI_LIB_DIR}/nifi-parquet-nar-${NIFI_VERSION}.nar \
    ${NIFI_LIB_DIR}/nifi-hadoop-libraries-nar-${NIFI_VERSION}.nar

USER nifi