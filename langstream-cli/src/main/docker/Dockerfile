#
#
# Copyright DataStax, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive

# Install some utilities
RUN echo 'Acquire::http::Timeout "30";\nAcquire::ftp::Timeout "30";\nAcquire::Retries "3";' > /etc/apt/apt.conf.d/99timeout_and_retries \
     && apt-get update \
     && apt-get -y dist-upgrade \
     && apt-get -y install --no-install-recommends vim netcat dnsutils net-tools less procps iputils-ping zip unzip git jq \
                 curl ca-certificates wget apt-transport-https software-properties-common gpg-agent

# Install Eclipse Temurin Package
RUN mkdir -p /etc/apt/keyrings \
     && wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | tee /etc/apt/keyrings/adoptium.asc \
     && echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list \
     && apt-get update \
     && apt-get -y dist-upgrade \
     && apt-get -y install temurin-17-jdk \
     && export ARCH=$(uname -m | sed -r 's/aarch64/arm64/g' |  awk '!/arm64/{$0="amd64"}1') \
     && echo networkaddress.cache.ttl=1 >> /usr/lib/jvm/temurin-17-jdk-$ARCH/conf/security/java.security \

# Cleanup apt
RUN apt-get -y --purge autoremove \
     && apt-get autoclean \
     && apt-get clean \
     && rm -rf /var/lib/apt/lists/*

RUN mkdir /app && chmod g+w /app

# Add the runtime code at the end. This optimize docker layers to not depend on artifacts-specific changes.
ADD maven /app
WORKDIR /app

RUN chown -R 10000:10000 /app/conf
RUN echo "export PATH=$PATH:/app/bin" >> /.bashrc

# The UID must be non-zero. Otherwise, it is arbitrary. No logic should rely on its specific value.
USER 10000

LABEL org.opencontainers.image.source=https://github.com/LangStream/langstream
LABEL org.opencontainers.image.licenses=Apache-2.0