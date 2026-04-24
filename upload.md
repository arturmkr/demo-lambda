# API Test Guide

Set the API URL once:

```bash
export API_URL="https://l6bq0fbnud.execute-api.us-east-1.amazonaws.com"
```

## 0. Run Local Tests First

Run the Docker Compose LocalStack test flow from the project root:

```bash
./scripts/test-local.sh
```

This starts LocalStack, runs the Spring tests, and shuts everything down.

## 1. Upload One Image

Use the helper script from the project root.

Tea image:

```bash
IMAGE_PATH="images/tea1.jpg" bash scripts/upload-image.sh
```

Coffee image:

```bash
IMAGE_PATH="images/coffee1.jpg" bash scripts/upload-image.sh
```

Gun image:

```bash
IMAGE_PATH="images/gun.jpg" bash scripts/upload-image.sh
```

Knife image:

```bash
IMAGE_PATH="images/knife1.jpg" bash scripts/upload-image.sh
```

The upload response should return:

```json
{"imageId":"img-xxxxxxxx","status":"PROCESSING"}
```

Copy the returned `imageId`.

## 2. Check One Image

Replace the example id with the real one:

```bash
curl "$API_URL/images/img-xxxxxxxx"
```

Expected:

- Tea or coffee image: usually `APPROVED`
- Gun or knife image: should be `REJECTED`

Useful fields:

- `status`
- `tags`
- `description`
- `rejectionReason`
- `originalUrl`
- `thumbnailUrl`

If the image is still processing, wait a few seconds and run the same command again.

## 3. Search Approved Images By Tag

Search tea:

```bash
curl "$API_URL/images?tags=tea"
```

Search coffee:

```bash
curl "$API_URL/images?tags=coffee"
```

Expected:

- Approved images should appear in search results
- Rejected images should not appear in search results

## 4. What To Verify

- `tea1.jpg` or `tea2.jpg` uploads successfully and becomes `APPROVED`
- `coffee1.jpg` or `coffee2.jpg` uploads successfully and becomes `APPROVED`
- `gun.jpg` becomes `REJECTED`
- `knife1.jpg` or `knife2.jpg` becomes `REJECTED`
- Approved images can be found with `GET /images?tags=...`
- Rejected images are visible with `GET /images/{imageId}` but should not appear in tag search

## 5. If Upload Fails

Check upload Lambda logs:

```bash
aws logs tail /aws/lambda/image-catalog-upload --since 15m --follow
```

Check process Lambda logs:

```bash
aws logs tail /aws/lambda/image-catalog-process --since 15m --follow
```
