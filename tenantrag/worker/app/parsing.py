"""
Parse raw document bytes into plain text, based on MIME type / filename.

Supported: PDF, DOCX, TXT, Markdown, HTML, CSV. Unknown types fall back to a
best-effort UTF-8 decode.
"""

from __future__ import annotations

import csv
import io

from bs4 import BeautifulSoup
from docx import Document as DocxDocument
from markdown import markdown
from pypdf import PdfReader


def _from_pdf(data: bytes) -> str:
    reader = PdfReader(io.BytesIO(data))
    return "\n\n".join((page.extract_text() or "") for page in reader.pages)


def _from_docx(data: bytes) -> str:
    doc = DocxDocument(io.BytesIO(data))
    return "\n".join(p.text for p in doc.paragraphs)


def _from_html(data: bytes) -> str:
    soup = BeautifulSoup(data, "html.parser")
    return soup.get_text(separator="\n")


def _from_markdown(data: bytes) -> str:
    # Render MD -> HTML, then strip tags to plain text.
    html = markdown(data.decode("utf-8", errors="replace"))
    return BeautifulSoup(html, "html.parser").get_text(separator="\n")


def _from_csv(data: bytes) -> str:
    text = data.decode("utf-8", errors="replace")
    rows = list(csv.reader(io.StringIO(text)))
    if not rows:
        return ""
    header = rows[0]
    lines = []
    for row in rows[1:]:
        # Prefix each value with its header so a chunk keeps column meaning.
        pairs = [f"{h}: {v}" for h, v in zip(header, row)]
        lines.append("; ".join(pairs))
    return "\n".join(lines)


def parse(data: bytes, mime_type: str, filename: str) -> str:
    """Return plain text extracted from the document bytes."""
    mt = (mime_type or "").lower()
    name = (filename or "").lower()

    if "pdf" in mt or name.endswith(".pdf"):
        return _from_pdf(data)
    if "word" in mt or name.endswith(".docx"):
        return _from_docx(data)
    if "html" in mt or name.endswith((".html", ".htm")):
        return _from_html(data)
    if "markdown" in mt or name.endswith((".md", ".markdown")):
        return _from_markdown(data)
    if "csv" in mt or name.endswith(".csv"):
        return _from_csv(data)

    # text/plain and everything else: best-effort decode.
    return data.decode("utf-8", errors="replace")
