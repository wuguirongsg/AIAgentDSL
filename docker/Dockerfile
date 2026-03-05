FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app
COPY . .
RUN ./gradlew build -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the built CLI distribution
COPY --from=builder /app/agentdsl-cli/build/distributions/agentdsl-cli-* /app/dist/

# Unpack it
RUN cd /app/dist && unzip agentdsl-cli-*.zip && rm agentdsl-cli-*.zip && mv agentdsl-cli-* /app/agentdsl
ENV PATH="/app/agentdsl/bin:${PATH}"

# Setup workspace
WORKDIR /workspace
ENTRYPOINT ["agentdsl"]
CMD ["--help"]
