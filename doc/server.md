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

# SSL Certificate Path (directory containing privkey.pem and fullchain.pem)
SSL_CERT_PATH=/mnt/data/ravenbrain/certs

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

Create directories for MySQL data, backups, and SSL certificates on your external/mounted storage:

```bash
sudo mkdir -p /mnt/data/ravenbrain/mysql
sudo mkdir -p /mnt/data/ravenbrain/backup
sudo mkdir -p /mnt/data/ravenbrain/certs
sudo chown -R 999:999 /mnt/data/ravenbrain/mysql   # MySQL container runs as UID 999
sudo chown -R 999:999 /mnt/data/ravenbrain/backup
```

### 5. Configure SSL Certificates

The production deployment runs HTTPS on port 443. You need to provide SSL certificate files.

#### Certificate Files Required

Place the following PEM files in the certs directory (`/mnt/data/ravenbrain/certs/`):

| File | Description |
|------|-------------|
| `privkey.pem` | Private key |
| `fullchain.pem` | Full certificate chain (certificate + intermediates) |

#### Obtaining Certificates

**Option A: Let's Encrypt (free, automated)**

Use certbot to obtain certificates:

```bash
sudo apt install certbot
sudo certbot certonly --standalone -d ravenbrain.team1310.ca
```

Certificates are saved to `/etc/letsencrypt/live/ravenbrain.team1310.ca/`. Copy or symlink them:

```bash
sudo cp /etc/letsencrypt/live/ravenbrain.team1310.ca/privkey.pem /mnt/data/ravenbrain/certs/
sudo cp /etc/letsencrypt/live/ravenbrain.team1310.ca/fullchain.pem /mnt/data/ravenbrain/certs/
```

**Option B: Provided Certificate**

If you have certificate files from another source (e.g., your organization), copy them to the certs directory:

```bash
sudo cp /path/to/your/privkey.pem /mnt/data/ravenbrain/certs/
sudo cp /path/to/your/fullchain.pem /mnt/data/ravenbrain/certs/
```

#### Secure the Certificate Files

```bash
sudo chmod 600 /mnt/data/ravenbrain/certs/*.pem
sudo chown root:root /mnt/data/ravenbrain/certs/*.pem
```

#### Certificate Renewal

If using Let's Encrypt, set up automatic renewal:

```bash
sudo crontab -e
```

Add:

```cron
0 3 * * * certbot renew --quiet && cp /etc/letsencrypt/live/ravenbrain.team1310.ca/*.pem /mnt/data/ravenbrain/certs/ && docker restart ravenbrain-app
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

The production deployment runs HTTPS only on port 443. HTTP (port 80/8888) is not exposed - all traffic must use HTTPS.

### Firewall Configuration

Open port 443 on your firewall/router and forward to the server:

```bash
# Example using ufw (Ubuntu)
sudo ufw allow 443/tcp
```

### DNS Configuration

Create a DNS A record pointing your domain to the server's IP address:

```
ravenbrain.team1310.ca.  A  <server-ip-address>
```

### Verify HTTPS

Once deployed, verify the certificate is working:

```bash
curl -I https://ravenbrain.team1310.ca/api/ping
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

### SSL Certificate Errors

If the application fails to start with SSL errors:

1. Verify certificate files exist and are readable:

```bash
ls -la /mnt/data/ravenbrain/certs/
```

2. Verify certificate file format (should be PEM):

```bash
openssl x509 -in /mnt/data/ravenbrain/certs/fullchain.pem -text -noout | head -20
```

3. Verify the private key matches the certificate:

```bash
# These two commands should output the same modulus
openssl x509 -noout -modulus -in /mnt/data/ravenbrain/certs/fullchain.pem | openssl md5
openssl rsa -noout -modulus -in /mnt/data/ravenbrain/certs/privkey.pem | openssl md5
```

4. Check the app container logs for SSL-specific errors:

```bash
docker compose logs app | grep -i ssl
```
