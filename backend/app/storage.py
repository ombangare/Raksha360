"""
Raksha360 — Multimodal Complaint Attachment Storage Layer
Provides an abstract StorageProvider interface supporting:
1. LocalStorageProvider (for local development and persistent volume mounts)
2. S3StorageProvider (for AWS S3, Cloudflare R2, and Supabase Object Storage)
Ensures ephemeral cloud containers fail-closed rather than silently losing evidence.
"""

from __future__ import annotations

import os
import re
import logging
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Optional

logger = logging.getLogger("raksha360.storage")


class StorageProvider(ABC):
    @abstractmethod
    def save(self, report_id: str, attachment_id: str, file_name: str, content: bytes) -> str:
        """Saves file content and returns a persistent storage path or URI."""
        pass

    @abstractmethod
    def get_path(self, storage_path: str) -> Optional[Path]:
        """Returns local Path if available locally, or None if remote-only."""
        pass

    @abstractmethod
    def read(self, storage_path: str) -> bytes:
        """Reads file bytes from storage."""
        pass

    @abstractmethod
    def delete(self, storage_path: str) -> bool:
        """Deletes file from storage."""
        pass

    @abstractmethod
    def is_durable(self) -> bool:
        """Returns True if storage persists through ephemeral container restarts."""
        pass


class LocalStorageProvider(StorageProvider):
    def __init__(self, base_dir: Path):
        self.base_dir = Path(base_dir)
        self.base_dir.mkdir(parents=True, exist_ok=True)
        self.env = os.getenv("RAKSHA360_ENV", os.getenv("ENVIRONMENT", "development")).strip().lower()
        self.has_persistent_disk = os.getenv("RENDER_PERSISTENT_DISK", "false").strip().lower() in ("true", "1", "yes")

    def save(self, report_id: str, attachment_id: str, file_name: str, content: bytes) -> str:
        safe_name = re.sub(r"[^a-zA-Z0-9_.-]", "_", file_name)
        target_dir = self.base_dir / report_id
        target_dir.mkdir(parents=True, exist_ok=True)
        target_file = target_dir / f"{attachment_id}_{safe_name}"
        target_file.write_bytes(content)
        return str(target_file)

    def get_path(self, storage_path: str) -> Optional[Path]:
        p = Path(storage_path)
        return p if p.is_file() else None

    def read(self, storage_path: str) -> bytes:
        p = Path(storage_path)
        if not p.is_file():
            raise FileNotFoundError(f"Attachment missing from local storage: {storage_path}")
        return p.read_bytes()

    def delete(self, storage_path: str) -> bool:
        try:
            p = Path(storage_path)
            if p.is_file():
                p.unlink()
                return True
        except Exception as e:
            logger.warning("Failed to delete local attachment %s: %s", storage_path, e)
        return False

    def is_durable(self) -> bool:
        # Non-production local dev is considered durable enough for development.
        # Production on Render requires an explicit persistent disk flag.
        if self.env in ("production", "prod"):
            return self.has_persistent_disk
        return True


class S3StorageProvider(StorageProvider):
    def __init__(
        self,
        bucket_name: str,
        endpoint_url: Optional[str] = None,
        access_key_id: Optional[str] = None,
        secret_access_key: Optional[str] = None,
        region_name: str = "auto",
    ):
        self.bucket_name = bucket_name
        self.endpoint_url = endpoint_url
        self.access_key_id = access_key_id
        self.secret_access_key = secret_access_key
        self.region_name = region_name
        self._s3_client = None

    def _get_client(self):
        if self._s3_client is None:
            import boto3
            self._s3_client = boto3.client(
                "s3",
                endpoint_url=self.endpoint_url,
                aws_access_key_id=self.access_key_id,
                aws_secret_access_key=self.secret_access_key,
                region_name=self.region_name,
            )
        return self._s3_client

    def save(self, report_id: str, attachment_id: str, file_name: str, content: bytes) -> str:
        safe_name = re.sub(r"[^a-zA-Z0-9_.-]", "_", file_name)
        key = f"reports/{report_id}/{attachment_id}_{safe_name}"
        client = self._get_client()
        client.put_object(
            Bucket=self.bucket_name,
            Key=key,
            Body=content,
        )
        return f"s3://{self.bucket_name}/{key}"

    def get_path(self, storage_path: str) -> Optional[Path]:
        return None  # Remote object store, stream via read()

    def read(self, storage_path: str) -> bytes:
        client = self._get_client()
        key = storage_path.replace(f"s3://{self.bucket_name}/", "")
        response = client.get_object(Bucket=self.bucket_name, Key=key)
        return response["Body"].read()

    def delete(self, storage_path: str) -> bool:
        try:
            client = self._get_client()
            key = storage_path.replace(f"s3://{self.bucket_name}/", "")
            client.delete_object(Bucket=self.bucket_name, Key=key)
            return True
        except Exception as e:
            logger.warning("Failed to delete S3 attachment %s: %s", storage_path, e)
            return False

    def is_durable(self) -> bool:
        return True


_global_storage_provider: Optional[StorageProvider] = None


def get_storage_provider(data_dir: Optional[Path] = None) -> StorageProvider:
    global _global_storage_provider
    if _global_storage_provider is not None:
        return _global_storage_provider

    s3_bucket = os.getenv("S3_BUCKET_NAME", os.getenv("ATTACHMENT_STORAGE_BUCKET", "")).strip()
    if s3_bucket:
        endpoint = os.getenv("S3_ENDPOINT_URL", os.getenv("SUPABASE_S3_ENDPOINT", "")).strip() or None
        access_key = os.getenv("AWS_ACCESS_KEY_ID", os.getenv("S3_ACCESS_KEY_ID", "")).strip() or None
        secret_key = os.getenv("AWS_SECRET_ACCESS_KEY", os.getenv("S3_SECRET_ACCESS_KEY", "")).strip() or None
        region = os.getenv("AWS_REGION", "auto").strip()
        try:
            _global_storage_provider = S3StorageProvider(
                bucket_name=s3_bucket,
                endpoint_url=endpoint,
                access_key_id=access_key,
                secret_access_key=secret_key,
                region_name=region,
            )
            logger.info("Configured S3StorageProvider with bucket: %s", s3_bucket)
            return _global_storage_provider
        except Exception as e:
            logger.error("Failed to initialize S3StorageProvider: %s", e)

    base = (data_dir or (Path(__file__).resolve().parent.parent / "data")) / "attachments"
    _global_storage_provider = LocalStorageProvider(base)
    return _global_storage_provider
