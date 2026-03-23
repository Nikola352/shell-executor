# Shell Executor

A REST API for running shell commands on remote machines. Submit a command, get back an execution ID, and poll for results. 

Each command runs in an isolated environment: a fresh Docker container or a new EC2 instance, that is provisioned on demand and torn down when the command finishes. 

Required resources (cpu and memory) can be specified when requesting execution.

## How it works

```
POST /executions  →  queued (ID returned immediately)
                  →  in progress (executor provisioned, command running)
                  →  finished / failed (stdout, stderr, exit code available)
GET  /executions/{id}  →  poll for current status and results
```

The API is non-blocking: submission returns right away and you poll for results.

## Executors

### Docker

Starts a fresh Alpine Linux container per execution. Resources (CPU, memory) are applied as Docker constraints. Works against any Docker host: local socket or a remote machine via TCP. The container is stopped and removed as soon as the command completes.

**Requirements:** Docker accessible at the configured host (default: local Unix socket).

### AWS EC2

Launches a fresh EC2 instance per execution, SSHs in to run the command, then terminates the instance. The cheapest instance type satisfying the requested CPU and memory is selected automatically.

**Requirements:** AWS account, configured credentials, a key pair, security group, and subnet. See [AWS_SETUP.md](AWS_SETUP.md) for step-by-step instructions.

> **Cost warning:** Each execution provisions and terminates a real EC2 instance. You will be charged for instance uptime (typically under a minute), EBS volume, and data transfer.

Pre-warmed pools and autoscaling are not currently implemented but are natural next steps that would integrate cleanly with the existing `ExecutorProvider` abstraction.

## Requirements

- Java 21
- Docker (for the `docker` executor, default)
- PostgreSQL (or use `docker-compose up` to start one)
- AWS account + credentials (for the `aws` executor only)

## Running

```bash
# Copy and fill in configuration (optional for the docker executor with default settings)
cp .env.example .env
# edit .env as needed, then:
source .env

# Start PostgreSQL
docker-compose up -d

# Start the API on port 8080
./gradlew run
```

`.env.example` documents all available variables with their defaults. For the Docker executor on a local socket, no changes are needed. For the AWS executor, you will need to fill in the AWS-specific values, see [AWS_SETUP.md](AWS_SETUP.md).

Open `http://localhost:8080` for the web UI, or `http://localhost:8080/openapi` for the Swagger UI.

## API

### Submit a command

```bash
curl -X POST http://localhost:8080/executions \
  -H "Content-Type: application/json" \
  -d '{"command": "echo hello", "resources": {"cpuCount": 1, "memoryMb": 512}}'
```

```json
{"id": 1}
```

### Poll for results

```bash
curl http://localhost:8080/executions/1
```

```json
{
  "id": 1,
  "command": "echo hello",
  "status": "FINISHED",
  "exitCode": 0,
  "stdout": "hello\n",
  "stderr": ""
}
```

Status values: `QUEUED` → `IN_PROGRESS` → `FINISHED` | `FAILED`

### Idempotency

Pass an `Idempotency-Key` header to avoid duplicate submissions (e.g. on retry). If an execution with that key already exists, its ID is returned without creating a new one.

```bash
curl -X POST http://localhost:8080/executions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"command": "uname -a", "resources": {"cpuCount": 1, "memoryMb": 512}}'
```

## Configuration

Set via environment variables or directly in `src/main/resources/application.yaml`.

| Variable | Default | Description |
|----------|---------|-------------|
| `EXECUTOR_TYPE` | `docker` | `docker` or `aws` |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/shell-executor` | |
| `POSTGRES_USER` | `postgres` | |
| `POSTGRES_PASSWORD` | `postgres` | |
| `DOCKER_HOST` | `unix:///var/run/docker.sock` | Docker socket or `tcp://host:port` |

For AWS variables, see [AWS_SETUP.md](AWS_SETUP.md).

## Testing

Unit tests cover the executor logic and service layer. API integration tests run against a full Ktor application with an in-memory H2 database and stub executor, so no external services are needed.

```bash
./gradlew test         # run all unit and API tests
```

AWS integration tests (which provision real EC2 instances) are excluded from the default test task and must be run explicitly. See [AWS_SETUP.md](AWS_SETUP.md#8-integration-tests).
