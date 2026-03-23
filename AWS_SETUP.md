# AWS EC2 Executor Setup

This guide walks through everything needed to use the `aws` executor type, which provisions a fresh EC2 instance for every command execution and permanently terminates it when done.

> **Cost warning:** Each execution creates and terminates a real EC2 instance. You will be charged for instance uptime (typically a minute or less), data transfer, and any EBS volumes.

---

## Prerequisites

- An AWS account with billing enabled
- [AWS CLI](https://aws.amazon.com/cli/) installed
- Java 21

---

## 1. AWS Credentials

The application reads credentials from the AWS SDK's **default credential provider chain** — never from `application.yaml`. Configure them once with the CLI:

```bash
aws configure
```

Enter your **Access Key ID**, **Secret Access Key**, default region (`eu-central-1`), and output format (`json`). This writes to `~/.aws/credentials`:

```ini
[default]
aws_access_key_id     = AKIAIOSFODNN7EXAMPLE
aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

### Required IAM permissions

Create a dedicated IAM user (e.g. `shell-executor-app`) with programmatic access and attach this inline policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:RunInstances",
        "ec2:TerminateInstances",
        "ec2:DescribeInstances"
      ],
      "Resource": "*"
    }
  ]
}
```

For tighter production security, scope `ec2:RunInstances` to specific AMI IDs, instance type families, key pairs, and subnets using resource conditions.

---

## 2. EC2 Key Pair

1. Open the [EC2 console](https://console.aws.amazon.com/ec2/) → **Key Pairs** → **Create key pair**
2. Give it a name (e.g. `shell-executor-key`), select **RSA**, format **`.pem`**
3. Download the `.pem` file — **this is the only time you can download it**
4. Move it to a safe location **outside the project** and restrict permissions:

```bash
mv ~/Downloads/shell-executor-key.pem ~/.ssh/
chmod 400 ~/.ssh/shell-executor-key.pem
```

Note the **key pair name** — you will use it in `application.yaml`.

---

## 3. Security Group

The executor connects to instances via SSH. Create a security group that allows inbound TCP on port 22:

```bash
# Create the security group
aws ec2 create-security-group \
  --group-name shell-executor-ssh \
  --description "Shell Executor SSH access" \
  --region eu-central-1

# Allow SSH from your current IP
aws ec2 authorize-security-group-ingress \
  --group-name shell-executor-ssh \
  --protocol tcp \
  --port 22 \
  --cidr $(curl -s https://checkip.amazonaws.com)/32 \
  --region eu-central-1
```

Note the **Security Group ID** (`sg-...`) printed by the first command.

> For development only, `--cidr 0.0.0.0/0` is convenient but exposes SSH publicly. Prefer your own IP.

---

## 4. VPC and Subnet

Instances need a **public subnet** (with auto-assign public IPv4 enabled) so the executor can reach them over SSH.

To find a suitable subnet in the default VPC:

```bash
aws ec2 describe-subnets \
  --filters "Name=default-for-az,Values=true" \
  --query "Subnets[0].{SubnetId:SubnetId,AZ:AvailabilityZone,AutoPublicIp:MapPublicIpOnLaunch}" \
  --region eu-central-1
```

Note the **Subnet ID** (`subnet-...`).

---

## 5. Ubuntu AMI

AMI IDs are region-specific and updated regularly. Find the latest Ubuntu 22.04 LTS AMI for your region:

```bash
aws ec2 describe-images \
  --owners 099720109477 \
  --filters \
    "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*" \
    "Name=state,Values=available" \
  --query "sort_by(Images,&CreationDate)[-1].ImageId" \
  --output text \
  --region eu-central-1
```

The default value in `application.yaml` (`ami-05852c5f195d545ea`) was current for `eu-central-1` at the time of writing but may become outdated — use the command above to verify.

---

## 6. Application Configuration

Edit `src/main/resources/application.yaml` and fill in the `aws:` section:

```yaml
executor:
  type: aws   # switch from "docker" to "aws"

aws:
  region: "eu-central-1"
  ami-id: "ami-05852c5f195d545ea"             # verify with the CLI command above
  key-name: "shell-executor-key"              # key pair name from Step 2
  pem-path: "/home/youruser/.ssh/shell-executor-key.pem"  # absolute path to .pem
  security-group-id: "sg-0123456789abcdef0"  # from Step 3
  subnet-id: "subnet-0123456789abcdef0"       # from Step 4
  ssh-user: "ubuntu"
  ssh-port: 22
  instance-timeout-seconds: 120
  ssh-timeout-seconds: 120
  ssh-retry-interval-ms: 5000
```

The `.pem` file path must be **absolute** and point to a location **outside the project directory**. The `.gitignore` already blocks `*.pem` files from being committed.

---

## 7. Running the Application

```bash
docker-compose up -d      # start PostgreSQL
./gradlew run             # start the API on port 8080
```

Test with a sample request:

```bash
curl -s -X POST http://localhost:8080/executions \
  -H "Content-Type: application/json" \
  -d '{"command":"echo hello from EC2","resources":{"cpuCount":1,"memoryMb":512}}' | jq .

# note the returned id, then poll until FINISHED:
curl -s http://localhost:8080/executions/{id} | jq .
```

Expect a delay of 60–90 seconds on the first `acquire` while the instance starts and SSH becomes available.

---

## 8. Integration Tests

Integration tests require real AWS credentials and **will create and terminate EC2 instances**:

```bash
./gradlew integrationTest --tests "com.github.nikola352.executor.aws.AwsExecutorProviderIT"
```

These are excluded from `./gradlew test` (the default test task) and must be run explicitly.

---

## Instance Type Selection

The executor automatically selects the cheapest EC2 instance type satisfying the requested CPU and memory (`ResourceRequirements`). The selection list is defined in `InstanceTypeSelector.kt`:

| Instance   | vCPU | Memory   |
|------------|------|----------|
| t3.nano    | 2    | 512 MiB  |
| t3.micro   | 2    | 1 GiB    |
| t3.small   | 2    | 2 GiB    |
| t3.medium  | 2    | 4 GiB    |
| t3.large   | 2    | 8 GiB    |
| t3.xlarge  | 4    | 16 GiB   |
| t3.2xlarge | 8    | 32 GiB   |
| m5.xlarge  | 4    | 16 GiB   |
| m5.2xlarge | 8    | 32 GiB   |
| m5.4xlarge | 16   | 64 GiB   |

The t3 family is preferred. `m5` serves as a fallback for requirements exceeding t3.2xlarge.
