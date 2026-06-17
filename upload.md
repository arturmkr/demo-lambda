# API Test Guide With Cognito

This API is protected by the API Gateway Cognito authorizer. Every API call must include:

```bash
Authorization: Bearer <Cognito ID token>
```

## 0. Deploy And Read Stack Outputs

After the CI/CD pipeline deploys the stack, read the API URL and Cognito IDs:

```bash
export STACK_NAME="image-catalog"
export REGION="us-east-1"

export API_URL="$(aws cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --region "$REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='ApiBaseUrl'].OutputValue" \
  --output text)"

export USER_POOL_ID="$(aws cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --region "$REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='CognitoUserPoolId'].OutputValue" \
  --output text)"

export CLIENT_ID="$(aws cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --region "$REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='CognitoUserPoolClientId'].OutputValue" \
  --output text)"
```

## 1. Create Demo Users Manually

CloudFormation creates the Cognito User Pool, app client, and groups. Demo users are created manually so users are not hardcoded in IaC.

```bash
export USER_PASSWORD='ReplaceMe-User-123!'
export ADMIN_PASSWORD='ReplaceMe-Admin-123!'

aws cognito-idp admin-create-user \
  --user-pool-id "$USER_POOL_ID" \
  --region "$REGION" \
  --username user@example.com \
  --user-attributes Name=email,Value=user@example.com Name=email_verified,Value=true

aws cognito-idp admin-set-user-password \
  --user-pool-id "$USER_POOL_ID" \
  --region "$REGION" \
  --username user@example.com \
  --password "$USER_PASSWORD" \
  --permanent

aws cognito-idp admin-add-user-to-group \
  --user-pool-id "$USER_POOL_ID" \
  --region "$REGION" \
  --username user@example.com \
  --group-name USER

aws cognito-idp admin-create-user \
  --user-pool-id "$USER_POOL_ID" \
  --region "$REGION" \
  --username admin@example.com \
  --user-attributes Name=email,Value=admin@example.com Name=email_verified,Value=true

aws cognito-idp admin-set-user-password \
  --user-pool-id "$USER_POOL_ID" \
  --region "$REGION" \
  --username admin@example.com \
  --password "$ADMIN_PASSWORD" \
  --permanent

aws cognito-idp admin-add-user-to-group \
  --user-pool-id "$USER_POOL_ID" \
  --region "$REGION" \
  --username admin@example.com \
  --group-name ADMIN
```

If the users already exist, skip `admin-create-user` and rerun `admin-set-user-password` plus `admin-add-user-to-group`.

## 2. Get Cognito ID Tokens

Authenticate as `USER`:

```bash
export USER_ID_TOKEN="$(aws cognito-idp admin-initiate-auth \
  --user-pool-id "$USER_POOL_ID" \
  --client-id "$CLIENT_ID" \
  --region "$REGION" \
  --auth-flow ADMIN_USER_PASSWORD_AUTH \
  --auth-parameters USERNAME=user@example.com,PASSWORD="$USER_PASSWORD" \
  --query "AuthenticationResult.IdToken" \
  --output text)"
```

Authenticate as `ADMIN`:

```bash
export ADMIN_ID_TOKEN="$(aws cognito-idp admin-initiate-auth \
  --user-pool-id "$USER_POOL_ID" \
  --client-id "$CLIENT_ID" \
  --region "$REGION" \
  --auth-flow ADMIN_USER_PASSWORD_AUTH \
  --auth-parameters USERNAME=admin@example.com,PASSWORD="$ADMIN_PASSWORD" \
  --query "AuthenticationResult.IdToken" \
  --output text)"
```

Use the ID token for API Gateway:

```bash
export ID_TOKEN="$USER_ID_TOKEN"
```

## 3. Upload One Image As USER

Use the helper script from the project root.

Tea image:

```bash
ID_TOKEN="$USER_ID_TOKEN" IMAGE_PATH="images/tea1.jpg" bash scripts/upload-image.sh
```

Coffee image:

```bash
ID_TOKEN="$USER_ID_TOKEN" IMAGE_PATH="images/coffee2.jpg" bash scripts/upload-image.sh
```

Gun image:

```bash
ID_TOKEN="$USER_ID_TOKEN" IMAGE_PATH="images/gun.jpg" bash scripts/upload-image.sh
```

Knife image:

```bash
ID_TOKEN="$USER_ID_TOKEN" IMAGE_PATH="images/knife1.jpg" bash scripts/upload-image.sh
```

The upload response should return:

```json
{"imageId":"img-xxxxxxxx","status":"PROCESSING"}
```

Copy the returned `imageId`.

## 4. Check My Images As USER

```bash
curl -i "$API_URL/images/my" \
  -H "Authorization: Bearer $USER_ID_TOKEN"
```

Expected:

- The user's uploaded images are returned.
- Requests without `Authorization` return `401 Unauthorized`.

## 5. Verify USER Cannot Call Admin Endpoint

```bash
curl -i "$API_URL/images/all" \
  -H "Authorization: Bearer $USER_ID_TOKEN"
```

Expected:

```text
HTTP/2 403
```

## 6. Call Admin Endpoint As ADMIN

```bash
curl -i "$API_URL/images/all" \
  -H "Authorization: Bearer $ADMIN_ID_TOKEN"
```

Expected:

- `ADMIN` can see all image records.

Approve or reject an image:

```bash
export IMAGE_ID="img-xxxxxxxx"

curl -i -X POST "$API_URL/images/$IMAGE_ID/approve" \
  -H "Authorization: Bearer $ADMIN_ID_TOKEN"

curl -i -X POST "$API_URL/images/$IMAGE_ID/reject" \
  -H "Authorization: Bearer $ADMIN_ID_TOKEN"
```

## 7. Logs

Check upload Lambda logs:

```bash
aws logs tail /aws/lambda/image-catalog-upload --since 15m --follow
```

Check process Lambda logs:

```bash
aws logs tail /aws/lambda/image-catalog-process --since 15m --follow
```

Check admin decision logs:

```bash
aws logs tail /aws/lambda/image-catalog-approve --since 15m --follow
aws logs tail /aws/lambda/image-catalog-reject --since 15m --follow
```
