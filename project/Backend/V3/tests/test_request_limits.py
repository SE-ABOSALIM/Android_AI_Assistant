import unittest
from unittest.mock import AsyncMock, patch

from fastapi import FastAPI, Request
from fastapi.testclient import TestClient
from pydantic import ValidationError

from V3 import main
from V3.middleware.request_body_limit import RequestBodyLimitMiddleware
from V3.schemas import (
    AppCatalogEntry,
    AppCatalogRequest,
    CustomCommandMutationRequest,
    CustomCommandStep,
    PredictRequest,
)


class RequestLimitRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)

    def test_oversizedRawBody_returns413(self):
        response = self.client.post(
            "/predict",
            content=b"x" * (2 * 1024 * 1024 + 1),
            headers={"Content-Type": "application/json"},
        )

        self.assertEqual(413, response.status_code)

    def test_requestWithinBodyLimit_isAccepted(self):
        test_app = FastAPI()
        test_app.add_middleware(RequestBodyLimitMiddleware, max_body_bytes=1024)

        @test_app.post("/echo-size")
        async def echo_size(request: Request):
            return {"size": len(await request.body())}

        response = TestClient(test_app).post(
            "/echo-size",
            content=b"x" * 1024,
        )

        self.assertEqual(200, response.status_code)
        self.assertEqual(1024, response.json()["size"])

    def test_oversizedPredictText_isRejected(self):
        with self.assertRaises(ValidationError):
            PredictRequest(text="x" * 1025, language="TR")

    def test_excessiveAppCatalogEntries_areRejected(self):
        entries = [
            AppCatalogEntry(label=f"App {index}", package_name=f"com.example.app{index}")
            for index in range(2001)
        ]

        with self.assertRaises(ValidationError):
            AppCatalogRequest(
                session_id="session-id",
                device_id="device-id",
                catalog_version="catalog-v1",
                apps=entries,
            )

    def test_excessiveAppAliases_areRejected(self):
        with self.assertRaises(ValidationError):
            AppCatalogEntry(
                label="App",
                package_name="com.example.app",
                aliases=[f"alias-{index}" for index in range(21)],
            )

    def test_excessiveCustomCommandSteps_areRejected(self):
        steps = [CustomCommandStep(intent="go_back") for _ in range(51)]

        with self.assertRaises(ValidationError):
            CustomCommandMutationRequest(
                device_id="device-id",
                language="TR",
                name="Long workflow",
                steps=steps,
            )

    def test_oversizedCustomCommandParameters_areRejected(self):
        with self.assertRaises(ValidationError):
            CustomCommandStep(
                intent="write_text",
                parameters={"text": "x" * (16 * 1024)},
            )


class ChunkedRequestBodyLimitTests(unittest.IsolatedAsyncioTestCase):
    async def test_oversizedChunkedBody_returns413WithoutContentLength(self):
        sent_messages = []
        received_messages = [
            {"type": "http.request", "body": b"1234", "more_body": True},
            {"type": "http.request", "body": b"5678", "more_body": False},
        ]

        async def downstream(scope, receive, send):
            more_body = True
            while more_body:
                message = await receive()
                more_body = message.get("more_body", False)
            await send({"type": "http.response.start", "status": 200, "headers": []})
            await send({"type": "http.response.body", "body": b"ok"})

        async def receive():
            return received_messages.pop(0)

        async def send(message):
            sent_messages.append(message)

        middleware = RequestBodyLimitMiddleware(downstream, max_body_bytes=6)
        await middleware(
            {
                "type": "http",
                "method": "POST",
                "path": "/chunked",
                "headers": [],
                "query_string": b"",
                "client": ("testclient", 1234),
                "server": ("testserver", 80),
                "scheme": "http",
            },
            receive,
            send,
        )

        response_start = next(
            message for message in sent_messages if message["type"] == "http.response.start"
        )
        self.assertEqual(413, response_start["status"])


if __name__ == "__main__":
    unittest.main()
