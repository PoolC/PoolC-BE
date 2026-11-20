# Lightsail production deployment

The production workflow uses GitHub OIDC to publish an immutable image to ECR,
then uses SSM Run Command to update `palkia` and restart Nginx. No SSH key or
long-lived AWS credential is stored in GitHub. The ECR login password sent to
the server is a short-lived deployment credential.

Deployment identifiers and the GitHub OIDC role ARN are committed in
`deploy/production.json`; they are not GitHub variables or secrets.

Before enabling production deployment, configure the Lightsail server as an SSM
managed node. The server keeps `/home/ubuntu/backend/.env.production` as the
Docker runtime file. Its source of truth is the SecureString parameters under
`/poolc/prod/backend/`. Every deployment renders the runtime file from that
path before restarting the backend container.

The production workflow also installs the tracked Nginx configuration,
certificate-renewal files, backup script, cron schedule, and log rotation
configuration before it validates and starts the Compose services. The current
direct-JAR deployment layout is tracked in `docker-compose.legacy-runtime.yaml`.

## Project game builds

Project game ZIPs are unpacked in the admin browser and uploaded directly to a
private S3 bucket with short-lived presigned PUT URLs. They are delivered by a
dedicated CloudFront distribution on `games.poolc.org`; game HTML must not be
served from the `poolc.org` frontend origin. Deploy
`../games-build-cloudfront.yaml` in AWS CloudFormation, providing an ACM
certificate ARN issued in `us-east-1` for `games.poolc.org`. Point the
Cloudflare `games.poolc.org` CNAME at the distribution domain from the stack
output (DNS-only mode).

Add these SSM parameters under `/poolc/prod/backend/` before enabling game
uploads (the feature stays disabled until all three values are set):

- `GAME_BUILD_ENABLED`: `true` to enable uploads.
- `GAME_BUILD_S3_BUCKET`: the stack's `BucketName` output.
- `GAME_BUILD_PUBLIC_BASE_URL`: `https://games.poolc.org`.

The Lightsail runtime role needs the `s3:PutObject` permission for
`arn:aws:s3:::<game-build-bucket>/games/*`; the sample policy in
`deploy/iam/poolc-lightsail-runtime-least-privilege.json` uses the default
bucket name. The S3 CORS rules in the stack allow the production and local
admin pages to upload directly. The CloudFront distribution reads the private
bucket through Origin Access Control.
