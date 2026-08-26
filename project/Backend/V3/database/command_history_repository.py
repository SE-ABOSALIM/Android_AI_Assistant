from decimal import Decimal
from typing import Any, Dict, Optional
from uuid import UUID

from V3.database.connection import is_database_configured, open_database_connection
from V3.intents.registry import is_known_intent


_SAFE_LANGUAGES = {"AR", "EN", "TR"}
_SAFE_ERROR_CODES = {
    "APP_CATALOG_MISSING",
    "APP_CATALOG_STALE",
    "APP_MATCH_AMBIGUOUS",
    "APP_NOT_IN_CATALOG",
    "BARE_ALARM_TIME",
    "CUSTOM_COMMAND_NOT_FOUND",
    "LOW_CONFIDENCE",
    "MISSING_ALARM_SIGNAL",
    "MISSING_REQUIRED_SLOT",
    "MODEL_STOP_LISTENING_DISABLED",
    "STOP_LISTENING_TOO_SHORT",
    "UNKNOWN_COMMAND",
    "UNSUPPORTED_INTENT",
    "UNSUPPORTED_STOPWATCH",
    "WEAK_STOP_LISTENING_COMMAND",
}


async def record_command_history(
    *,
    device_ref_id: str,
    intent: str,
    language: str,
    accepted: bool,
    confidence: Optional[float],
    error_code: Optional[str],
    processing_time_ms: Optional[float],
) -> bool:
    safe_device_ref_id = _optional_uuid(device_ref_id)
    safe_intent = _safe_intent(intent)
    safe_language = _safe_language(language)
    safe_confidence = _bounded_float(confidence, minimum=0.0, maximum=1.0)
    safe_error_code = _safe_error_code(error_code)
    safe_processing_time_ms = _bounded_float(processing_time_ms, minimum=0.0)
    if (
        not is_database_configured()
        or safe_device_ref_id is None
        or safe_intent is None
        or safe_language is None
        or (confidence is not None and safe_confidence is None)
        or (error_code is not None and safe_error_code is None)
        or (processing_time_ms is not None and safe_processing_time_ms is None)
    ):
        return False

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return False

        async with connection.transaction():
            await connection.fetchval(
                """
                INSERT INTO command_history (
                    device_ref_id,
                    intent,
                    language,
                    accepted,
                    confidence,
                    error_code,
                    processing_time_ms
                )
                VALUES ($1, $2, $3, $4, $5, $6, $7)
                RETURNING id
                """,
                safe_device_ref_id,
                safe_intent,
                safe_language,
                bool(accepted),
                safe_confidence,
                safe_error_code,
                safe_processing_time_ms,
            )

        return True
    except Exception as exc:
        print(f"[database] failed to record command history | error={type(exc).__name__}", flush=True)
        return False
    finally:
        if connection is not None:
            await connection.close()


async def list_command_history(
    *,
    device_ref_id: str,
    limit: int,
    offset: int,
    query: Optional[str] = None,
) -> Dict[str, Any]:
    limit = max(1, min(int(limit or 20), 50))
    offset = max(0, int(offset or 0))

    if not is_database_configured():
        return _empty_history(limit, offset)

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return _empty_history(limit, offset)

        scope = _history_scope(device_ref_id)
        if scope is None:
            return _empty_history(limit, offset)

        where_sql, params = _where_clause(scope, query)
        total_count = int(await connection.fetchval(
            f"SELECT COUNT(*) FROM command_history WHERE {where_sql}",
            *params,
        ))
        successful_count = int(await connection.fetchval(
            f"SELECT COUNT(*) FROM command_history WHERE {scope.sql} AND accepted = true",
            *scope.params,
        ))
        failed_count = int(await connection.fetchval(
            f"SELECT COUNT(*) FROM command_history WHERE {scope.sql} AND accepted = false",
            *scope.params,
        ))

        query_params = [*params, limit, offset]
        limit_position = len(params) + 1
        offset_position = len(params) + 2
        rows = await connection.fetch(
            f"""
            SELECT
                id::text,
                intent,
                language,
                accepted,
                confidence,
                error_code,
                processing_time_ms,
                created_at
            FROM command_history
            WHERE {where_sql}
            ORDER BY created_at DESC
            LIMIT ${limit_position}
            OFFSET ${offset_position}
            """,
            *query_params,
        )

        return {
            "items": [_row_to_history_item(row) for row in rows],
            "total_count": total_count,
            "successful_count": successful_count,
            "failed_count": failed_count,
            "limit": limit,
            "offset": offset,
            "has_more": offset + len(rows) < total_count,
        }
    except Exception as exc:
        print(f"[database] failed to list command history | error={type(exc).__name__}", flush=True)
        return _empty_history(limit, offset)
    finally:
        if connection is not None:
            await connection.close()


async def clear_command_history(*, device_ref_id: str) -> int:
    if not is_database_configured():
        return 0

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return 0

        scope = _history_scope(device_ref_id)
        if scope is None:
            return 0

        result = await connection.execute(
            f"DELETE FROM command_history WHERE {scope.sql}",
            *scope.params,
        )
        return _deleted_count(result)
    except Exception as exc:
        print(f"[database] failed to clear command history | error={type(exc).__name__}", flush=True)
        return 0
    finally:
        if connection is not None:
            await connection.close()


async def delete_command_history_item(
    *,
    history_id: str,
    device_ref_id: str,
) -> int:
    if not is_database_configured() or not _has_text(history_id):
        return 0

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return 0

        scope = _history_scope(device_ref_id)
        if scope is None:
            return 0

        result = await connection.execute(
            f"DELETE FROM command_history WHERE id = $1 AND {scope.shifted_sql(2)}",
            str(history_id).strip(),
            *scope.params,
        )
        return _deleted_count(result)
    except Exception as exc:
        print(f"[database] failed to delete command history item | error={type(exc).__name__}", flush=True)
        return 0
    finally:
        if connection is not None:
            await connection.close()


def _history_scope(device_ref_id: str):
    safe_device_ref_id = _optional_uuid(device_ref_id)
    if safe_device_ref_id is None:
        return None
    return _Scope("device_ref_id = $1", [safe_device_ref_id])


def _where_clause(scope, query: Optional[str]):
    if not _has_text(query):
        return scope.sql, scope.params

    search = f"%{str(query).strip()}%"
    query_position = len(scope.params) + 1
    return (
        f"{scope.sql} AND (COALESCE(intent, '') ILIKE ${query_position} "
        f"OR COALESCE(error_code, '') ILIKE ${query_position})",
        [*scope.params, search],
    )


def _row_to_history_item(row) -> Dict[str, Any]:
    return {
        "id": row["id"],
        "intent": row["intent"],
        "language": row["language"],
        "accepted": bool(row["accepted"]),
        "error_code": row["error_code"],
        "confidence": _optional_float(row["confidence"]),
        "processing_time_ms": _optional_float(row["processing_time_ms"]),
        "created_at": row["created_at"].isoformat(),
    }


def _empty_history(limit: int, offset: int) -> Dict[str, Any]:
    return {
        "items": [],
        "total_count": 0,
        "successful_count": 0,
        "failed_count": 0,
        "limit": limit,
        "offset": offset,
        "has_more": False,
    }


def _deleted_count(result: str) -> int:
    try:
        return int(str(result).split()[-1])
    except (ValueError, IndexError):
        return 0


def _bounded_float(value, *, minimum: float, maximum: Optional[float] = None) -> Optional[float]:
    if value is None:
        return None
    if isinstance(value, Decimal):
        parsed = float(value)
    else:
        try:
            parsed = float(value)
        except (TypeError, ValueError):
            return None
    if parsed < minimum or (maximum is not None and parsed > maximum):
        return None
    return parsed


def _optional_float(value) -> Optional[float]:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _optional_uuid(value) -> Optional[UUID]:
    try:
        return UUID(str(value))
    except (TypeError, ValueError, AttributeError):
        return None


def _safe_intent(value) -> Optional[str]:
    normalized = str(value or "").strip().upper()
    if not is_known_intent(normalized):
        return None
    return normalized


def _safe_error_code(value) -> Optional[str]:
    if value is None:
        return None
    normalized = str(value or "").strip().upper()
    if normalized not in _SAFE_ERROR_CODES:
        return None
    return normalized


def _safe_language(value) -> Optional[str]:
    normalized = str(value or "").strip().upper()
    if normalized not in _SAFE_LANGUAGES:
        return None
    return normalized


def _has_text(value) -> bool:
    return value is not None and str(value).strip() != ""


class _Scope:
    def __init__(self, sql: str, params):
        self.sql = sql
        self.params = params

    def shifted_sql(self, start_position: int) -> str:
        sql = self.sql
        for index in range(len(self.params), 0, -1):
            sql = sql.replace(f"${index}", f"${start_position + index - 1}")
        return sql
