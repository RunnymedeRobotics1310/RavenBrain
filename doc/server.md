# Raven Brain - Server Setup Instructions

This guide explains how to deploy RavenBrain to a production server using Docker.

## Prerequisites

### On the Server

- Linux server (Ubuntu/Debian recommended) or Raspberry Pi with 64-bit OS
- Docker Engine installed ([installation guide](https://docs.docker.com/engine/install/))
- Docker Compose plugin installed
- External storage mounted (for database persistence)

### On Your Development Machine

- Java 25 installed
- Docker installed (for building images)

## Building the Docker Image

On your development machine, build the production Docker image:

```bash
./gradlew dockerBuild
```

This creates the `ravenbrain:latest` image.

## Transferring the Image to the Server

### Option A: Docker Save/Load (No Registry Required)

Export the image to a file:

```bash
docker save ravenbrain:latest | gzip > ravenbrain-latest.tar.gz
```

Copy to the server:

```bash
scp ravenbrain-latest.tar.gz user@your-server:/home/user/
```

On the server, load the image:

```bash
gunzip -c ravenbrain-latest.tar.gz | docker load
```

### Option B: Use a Container Registry

Tag and push to your registry (Docker Hub, GitHub Container Registry, etc.):

```bash
docker tag ravenbrain:latest your-registry/ravenbrain:latest
docker push your-registry/ravenbrain:latest
```

On the server, pull the image:

```bash
docker pull your-registry/ravenbrain:latest
docker tag your-registry/ravenbrain:latest ravenbrain:latest
```

## Server Configuration

### 1. Create Deployment Directory

```bash
sudo mkdir -p /opt/ravenbrain
cd /opt/ravenbrain
```

### 2. Copy Docker Compose Files

Copy these files from the repository to `/opt/ravenbrain/` on the server:

- `docker-compose.yml`
- `docker-compose.prod.yml`

### 3. Create Environment File

Create `/opt/ravenbrain/.env` with production secrets:

```bash
# MySQL Configuration
MYSQL_ROOT_PASSWORD=<strong-root-password>
MYSQL_DATABASE=ravenbrain
MYSQL_USER=rb
MYSQL_PASSWORD=<strong-app-password>

# JWT Security
JWT_GENERATOR_SIGNATURE_SECRET=<random-64-char-string>

# Application Security
ENCRYPTION_SEED=<random-string>
SUPERUSER_PASSWORD=<superuser-password>
REGISTRATION_SECRET=<registration-secret>

# FRC API Credentials
FRC_USER=<your-frc-api-user>
FRC_KEY=<your-frc-api-key>

# Production Data Path
MYSQL_DATA_PATH=/mnt/data/ravenbrain

# Backup Configuration (optional, defaults shown)
# BACKUP_INTERVAL_SECONDS=86400
# BACKUP_RETENTION_DAYS=60
```

Secure the file:

```bash
sudo chmod 600 /opt/ravenbrain/.env
sudo chown root:root /opt/ravenbrain/.env
```

#### Security Rationale

This deployment uses a `.env` file for configuration secrets. This approach is simple and well-supported by Docker Compose, which automatically reads variables from `.env` in the working directory.

**Why this approach:**
- Simple to set up and maintain
- No additional tooling required
- Easy to update configuration without modifying compose files
- Works identically across bare metal, VMs, and LXC containers (e.g., Proxmox)

**Security considerations:**
- Secrets are stored in plain text on disk - ensure file permissions are restrictive (`chmod 600`, owned by root)
- Do not include the `.env` file in backups that leave the server
- The LXC container or VM hosting Docker should itself be properly secured

**Alternatives for higher-security environments:**
- Systemd `EnvironmentFile` directive to load secrets into the service
- External secret management (HashiCorp Vault, etc.)
- Docker Swarm secrets (requires Swarm mode)

For most self-hosted deployments, the `.env` file approach with proper file permissions provides adequate security.

### 4. Prepare Data Directories

Create directories for MySQL data and backups on your external/mounted storage:

```bash
sudo mkdir -p /mnt/data/ravenbrain/mysql
sudo mkdir -p /mnt/data/ravenbrain/backup
sudo chown -R 999:999 /mnt/data/ravenbrain/mysql   # MySQL container runs as UID 999
sudo chown -R 999:999 /mnt/data/ravenbrain/backup
```

## Starting RavenBrain

Start the containers in production mode:

```bash
cd /opt/ravenbrain
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Verify all three containers are running (mysql, app, backup):

```bash
docker compose ps
```

Check the application logs:

```bash
docker compose logs -f app
```

Check the backup container logs:

```bash
docker compose logs -f backup
```

## Auto-Start on Boot

Create a systemd service file at `/etc/systemd/system/ravenbrain.service`:

```ini
[Unit]
Description=RavenBrain Application
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/ravenbrain
ExecStart=/usr/bin/docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
ExecStop=/usr/bin/docker compose -f docker-compose.yml -f docker-compose.prod.yml down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
```

Enable and start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable ravenbrain
sudo systemctl start ravenbrain
```

## Operations

### View Logs

```bash
cd /opt/ravenbrain
docker compose logs -f app      # Application logs
docker compose logs -f mysql    # Database logs
docker compose logs -f backup   # Backup logs
```

### Restart Application

```bash
cd /opt/ravenbrain
docker compose restart app
```

### Stop Everything

```bash
cd /opt/ravenbrain
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
```

### Update Application

On your development machine:

```bash
./gradlew dockerBuild
docker save ravenbrain:latest | gzip > ravenbrain-latest.tar.gz
scp ravenbrain-latest.tar.gz user@your-server:/home/user/
```

On the server:

```bash
cd /opt/ravenbrain
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
gunzip -c /home/user/ravenbrain-latest.tar.gz | docker load
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Automated Backups

The production deployment includes a backup sidecar container that automatically:

- Creates compressed database backups at a configurable interval (default: every 24 hours)
- Removes backups older than the retention period (default: 60 days)
- Writes backups to `/mnt/data/ravenbrain/backup` on the host

### Configuration

Set these environment variables in `.env` to customize backup behavior:

| Variable | Default | Description |
|----------|---------|-------------|
| `BACKUP_INTERVAL_SECONDS` | 86400 | Time between backups (86400 = 24 hours) |
| `BACKUP_RETENTION_DAYS` | 60 | Days to keep old backups |

### View Backup Status

```bash
docker compose logs -f backup
ls -la /mnt/data/ravenbrain/backup/
```

### Manual Backup

To trigger an immediate backup, restart the backup container:

```bash
docker compose restart backup
```

Or run mysqldump directly:

```bash
docker exec ravenbrain-mysql mysqldump -u root -p<root-password> \
    --single-transaction --routines --triggers \
    ravenbrain | gzip > /mnt/data/ravenbrain/backup/ravenbrain-manual-$(date +%Y%m%d-%H%M%S).sql.gz
```

### Restore Database

```bash
gunzip -c /mnt/data/ravenbrain/backup/ravenbrain-YYYYMMDD-HHMMSS.sql.gz | \
    docker exec -i ravenbrain-mysql mysql -u root -p<root-password> ravenbrain
```

## Network Configuration

RavenBrain listens on port 8888. To make it accessible:

### Option A: Direct Access

Open port 8888 on your firewall/router and forward to the server.

### Option B: Reverse Proxy (Recommended)

Use nginx or Caddy as a reverse proxy with HTTPS. Example nginx configuration:

```nginx
server {
    listen 443 ssl;
    server_name ravenbrain.yourdomain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:8888;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Troubleshooting

### Application Won't Start

Check if MySQL is healthy:

```bash
docker compose ps
docker compose logs mysql
```

### Database Connection Errors

Verify the app can reach MySQL:

```bash
docker exec ravenbrain-app ping -c 1 mysql
```

### Out of Disk Space

Check disk usage on the data volume:

```bash
df -h /mnt/data/ravenbrain
```
