"""
Worker entrypoint — consumes `documents.ingest` from RabbitMQ and runs the
ingestion pipeline for each message.

Message body (JSON, published by the Java backend):
    {"documentId": "...", "tenantId": "...", "storageKey": "...",
     "mimeType": "...", "filename": "..."}

Run:
    python -m app.main
"""

from __future__ import annotations

import asyncio
import json
import logging

import aio_pika

from app.config import settings
from app.pipeline import ingest_document

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
log = logging.getLogger("worker.main")


async def _handle(message: aio_pika.abc.AbstractIncomingMessage) -> None:
    async with message.process(requeue=False):
        payload = json.loads(message.body.decode("utf-8"))
        log.info("Received message: %s", payload)

        document_id = payload["documentId"]
        tenant_id = payload["tenantId"]
        storage_key = payload["storageKey"]
        mime_type = payload.get("mimeType", "application/octet-stream")
        filename = payload.get("filename", "upload")

        # The pipeline is synchronous (psycopg + httpx sync clients). Run it in a
        # thread so we don't block the asyncio event loop / heartbeats.
        await asyncio.to_thread(
            ingest_document, document_id, tenant_id, storage_key,
            mime_type, filename,
        )


async def main() -> None:
    log.info("Connecting to RabbitMQ at %s", settings.rabbitmq_url)
    connection = await aio_pika.connect_robust(settings.rabbitmq_url)

    async with connection:
        channel = await connection.channel()
        # Only hand us one message at a time (fair dispatch; ingestion is heavy).
        await channel.set_qos(prefetch_count=1)

        # Durable queue so messages survive a broker restart. The backend
        # publishes to this same queue name.
        queue = await channel.declare_queue(settings.ingest_queue, durable=True)

        log.info("Waiting for messages on '%s'. Ctrl+C to exit.",
                 settings.ingest_queue)
        await queue.consume(_handle)

        # Keep running forever.
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log.info("Worker stopped.")
