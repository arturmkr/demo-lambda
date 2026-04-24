# Serverless AI Image Catalog

Spring-based serverless image catalog built with AWS Lambda, API Gateway, S3, DynamoDB, Rekognition, CloudFormation, and GitHub Actions.

## Architecture

- `POST /images` uploads an image and stores its initial metadata in DynamoDB with status `PROCESSING`.
- S3 `ObjectCreated` on `original/` triggers image processing.
- Processing Lambda creates a thumbnail, calls Amazon Rekognition for labels and moderation, and updates DynamoDB.
- `GET /images?tags=tea,drink` scans approved images by tags.
- `GET /images/{imageId}` returns one image metadata record.

## Tech Stack

- Spring Boot 4
- Java 25
- Spring Cloud Function for AWS Lambda handlers
- AWS SDK for Java v2
- DynamoDB for metadata
- S3 for original and thumbnail files
- Amazon Rekognition for labels and moderation
- CloudFormation for infrastructure
- GitHub Actions for CI/CD

## Project Structure

- `build.gradle`
- `src/main/java/com/demo/catalog`
- `infra/01-artifact-bucket.yaml`
- `infra/02-image-catalog.yaml`
- `.github/workflows/deploy.yml`

## API Notes

`POST /images` supports:

- raw image bytes through API Gateway binary payloads
- JSON payloads shaped like:

```json
{
  "fileName": "tea.jpg",
  "contentType": "image/jpeg",
  "base64Data": "..."
}
```

Example response:

```json
{
  "imageId": "img-12345678",
  "status": "PROCESSING"
}
```

## Deploy Flow

1. Push to `main` or run the GitHub Actions workflow manually.
2. Workflow builds `app.jar`.
3. Workflow uploads the jar to the deployment S3 bucket.
4. Workflow deploys the CloudFormation stack.
5. Stack output returns the API Gateway base URL.

## Important Note

The Gradle wrapper jar is not committed in this workspace snapshot, so `./gradlew` will not run locally until the wrapper is generated once in a network-enabled environment.
