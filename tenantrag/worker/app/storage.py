"""Download document bytes from MinIO/S3."""

import boto3
from botocore.config import Config

from app.config import settings


def _client():
    # Path-style addressing is required for MinIO (http://host:9000/bucket/key).
    return boto3.client(
        "s3",
        endpoint_url=settings.storage_endpoint,
        region_name=settings.storage_region,
        aws_access_key_id=settings.storage_access_key,
        aws_secret_access_key=settings.storage_secret_key,
        config=Config(s3={"addressing_style": "path"}),
    )


def download_bytes(storage_key: str) -> bytes:
    """Fetch the full object for the given key from the documents bucket."""
    resp = _client().get_object(Bucket=settings.storage_bucket, Key=storage_key)
    return resp["Body"].read()
