"""
Dev helper: publish a fake `documents.ingest` message so you can test the worker
without the Java backend wired up yet.

Usage (venv active, from tenantrag/worker):
    python -m app.publish_test <documentId> <tenantId> <storageKey> <mimeType> <filename>

Example:
    python -m app.publish_test 11111111-1111-1111-1111-111111111111 \
        22222222-2222-2222-2222-222222222222 \
        22222222-2222-2222-2222-222222222222/11111111-1111-1111-1111-111111111111/a.txt \
        text/plain a.txt
"""

from __future__ import annotations

import asyncio
import json
import sys

import aio_pika

from app.config import settings


async def main() -> None:
    if len(sys.argv) != 6:
        print(__doc__)
        sys.exit(1)

    document_id, tenant_id, storage_key, mime_type, filename = sys.argv[1:6]
    body = {
        "documentId": document_id,
        "tenantId": tenant_id,
        "storageKey": storage_key,
        "mimeType": mime_type,
        "filename": filename,
    }

    connection = await aio_pika.connect_robust(settings.rabbitmq_url)
    async with connection:
        channel = await connection.channel()
        await channel.declare_queue(settings.ingest_queue, durable=True)
        await channel.default_exchange.publish(
            aio_pika.Message(
                body=json.dumps(body).encode("utf-8"),
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            ),
            routing_key=settings.ingest_queue,
        )
        print(f"Published to '{settings.ingest_queue}': {body}")


if __name__ == "__main__":
    asyncio.run(main())
