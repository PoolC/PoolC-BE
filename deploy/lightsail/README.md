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
`../games-build-cloudfront.yaml` in `ap-northeast-2` AWS CloudFormation, providing an ACM
certificate ARN issued in `us-east-1` for `games.poolc.org`. Add the ACM DNS
validation CNAME to the `poolc.org` Route53 hosted zone before creating the
stack. The stack creates Route53 A and AAAA aliases for the game host.

Add these SSM parameters under `/poolc/prod/backend/` before enabling game
uploads (the feature stays disabled until all three values are set):

- `GAME_BUILD_ENABLED`: `true` to enable uploads.
- `GAME_BUILD_S3_BUCKET`: the stack's `BucketName` output.
- `GAME_BUILD_PUBLIC_BASE_URL`: `https://games.poolc.org`.

The backend signs production uploads as the `poolc-lightsail-runtime` IAM user.
That user needs `s3:PutObject` and `s3:DeleteObject` on
`arn:aws:s3:::<game-build-bucket>/games/*`, plus `s3:ListBucket` on the bucket
restricted to the `games/*` prefix. The policy in
`deploy/iam/poolc-lightsail-runtime-least-privilege.json` contains those scoped
permissions. The S3 CORS rules in the stack allow the production and local admin
pages to upload directly. CloudFront reads the private bucket through Origin
Access Control.
