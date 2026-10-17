import os
import pytest
from pathlib import Path
from backend.app.storage import LocalStorageProvider, S3StorageProvider, get_storage_provider


def test_local_storage_provider_crud(tmp_path):
    storage = LocalStorageProvider(tmp_path)
    
    # Save
    report_id = "test-report-001"
    att_id = "att-uuid-123"
    content = b"sample image binary content"
    path_str = storage.save(report_id, att_id, "evidence.jpg", content)
    
    assert Path(path_str).exists()
    assert Path(path_str).read_bytes() == content
    
    # Read
    read_bytes = storage.read(path_str)
    assert read_bytes == content
    
    # Get local path
    local_p = storage.get_path(path_str)
    assert local_p is not None
    assert local_p.is_file()
    
    # Delete
    deleted = storage.delete(path_str)
    assert deleted is True
    assert not Path(path_str).exists()


def test_local_storage_durability_check(monkeypatch, tmp_path):
    # In development: durable
    monkeypatch.setenv("RAKSHA360_ENV", "development")
    dev_storage = LocalStorageProvider(tmp_path)
    assert dev_storage.is_durable() is True
    
    # In production without persistent disk: NOT durable (fail-closed)
    monkeypatch.setenv("RAKSHA360_ENV", "production")
    monkeypatch.delenv("RENDER_PERSISTENT_DISK", raising=False)
    prod_storage = LocalStorageProvider(tmp_path)
    assert prod_storage.is_durable() is False
    
    # In production with persistent disk: durable
    monkeypatch.setenv("RENDER_PERSISTENT_DISK", "true")
    prod_persistent_storage = LocalStorageProvider(tmp_path)
    assert prod_persistent_storage.is_durable() is True
