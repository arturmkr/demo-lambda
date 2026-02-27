import urllib.parse
import boto3
import io
from PIL import Image

s3 = boto3.client("s3")

THUMB_SIZE = (100, 100)
DEST_PREFIX = "thumbnail/"
SRC_PREFIX = "original/"


def lambda_handler(event, context):
    # One record is enough for the lab
    record = event["Records"][0]
    bucket = record["s3"]["bucket"]["name"]
    key = urllib.parse.unquote_plus(record["s3"]["object"]["key"])

    # Only handle original/
    if not key.startswith(SRC_PREFIX):
        return {"ignored": key}

    # Download original
    obj = s3.get_object(Bucket=bucket, Key=key)
    data = obj["Body"].read()

    # Resize
    img = Image.open(io.BytesIO(data))
    # Convert to RGB to avoid issues with PNG/WEBP modes when saving JPEG
    if img.mode not in ("RGB", "L"):
        img = img.convert("RGB")
    img.thumbnail(THUMB_SIZE)

    # Decide output format: keep extension if it's jpeg/png, else jpeg
    lower = key.lower()
    if lower.endswith(".png"):
        out_format = "PNG"
        content_type = "image/png"
    else:
        out_format = "JPEG"
        content_type = "image/jpeg"

    out = io.BytesIO()
    if out_format == "JPEG":
        img.save(out, format=out_format, quality=85, optimize=True)
    else:
        img.save(out, format=out_format, optimize=True)
    out.seek(0)

    dst_key = DEST_PREFIX + key[len(SRC_PREFIX):]

    # Upload thumbnail
    s3.put_object(
        Bucket=bucket,
        Key=dst_key,
        Body=out.getvalue(),
        ContentType=content_type,
    )

    return {"source": key, "thumbnail": dst_key}