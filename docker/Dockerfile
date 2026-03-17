# docker build -t agentdsl-cli -f docker/Dockerfile .
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the pre-built CLI distribution
COPY agentdsl-cli/build/distributions/agentdsl-cli-*.zip /app/dist/

# Unpack it
RUN cd /app/dist && \
    unzip agentdsl-cli-*.zip && \
    rm agentdsl-cli-*.zip && \
    mv agentdsl-cli-* /app/agentdsl

ENV PATH="/app/agentdsl/bin:${PATH}"

# Setup workspace
WORKDIR /workspace
ENTRYPOINT ["agentdsl-cli"]
CMD ["--help"]
