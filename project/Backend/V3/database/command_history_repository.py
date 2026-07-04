import json
from decimal import Decimal
from typing import Any, Dict, Optional

from V3.database.connection import is_database_configured, open_database_connection


async def record_command_history(
    *,
    text: str,
    language: str,
    response: Dict[str, Any],
    session_id: Optional[str],
    device_id: Optional[str],
    processing_time_ms: Optional[float],
) -> bool:
    if not is_database_configured() or not _has_text(text):
        return False

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return False

        async with connection.transaction():
            database_device_id = await _ensure_device(
                connection,
                device_id=device_id,
                language=language,
            )
            accepted = bool(response.get("accepted"))
            await connection.fetchval(
                """
                INSERT INTO command_history (
                    device_ref_id,
                    session_id,
                    text,
                    language,
                    intent,
                    parameters_json,
                    accepted,
                    confidence,
                    result_status,
                    error_code,
                    processing_time_ms
                )
                VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7, $8, $9, $10, $11)
                RETURNING id
                """,
                database_device_id,
                _clean_text(session_id),
                str(text).strip(),
                _clean_text(language, uppercase=True) or "TR",
                _clean_text(response.get("intent"), uppercase=True),
                json.dumps(response.get("parameters") or {}, ensure_ascii=False),
                accepted,
                _optional_float(response.get("confidence")),
                "successful" if accepted else "failed",
                _clean_text(response.get("error_code"), uppercase=True),
                _optional_float(processing_time_ms),
            )

        return True
    except Exception as exc:
        print(f"[database] failed to record command history | error={exc}", flush=True)
        return False
    finally:
        if connection is not None:
            await connection.close()


async def list_command_history(
    *,
    session_id: Optional[str],
    device_id: Optional[str],
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

        scope = await _history_scope(connection, device_id=device_id, session_id=session_id)
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
                text,
                language,
                intent,
                parameters_json,
                accepted,
                confidence,
                result_status,
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
        print(f"[database] failed to list command history | error={exc}", flush=True)
        return _empty_history(limit, offset)
    finally:
        if connection is not None:
            await connection.close()


async def clear_command_history(*, session_id: Optional[str], device_id: Optional[str]) -> int:
    if not is_database_configured():
        return 0

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return 0

        scope = await _history_scope(connection, device_id=device_id, session_id=session_id)
        if scope is None:
            return 0

        result = await connection.execute(
            f"DELETE FROM command_history WHERE {scope.sql}",
            *scope.params,
        )
        return _deleted_count(result)
    except Exception as exc:
        print(f"[database] failed to clear command history | error={exc}", flush=True)
        return 0
    finally:
        if connection is not None:
            await connection.close()


async def delete_command_history_item(
    *,
    history_id: str,
    session_id: Optional[str],
    device_id: Optional[str],
) -> int:
    if not is_database_configured() or not _has_text(history_id):
        return 0

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return 0

        scope = await _history_scope(connection, device_id=device_id, session_id=session_id)
        if scope is None:
            return 0

        result = await connection.execute(
            f"DELETE FROM command_history WHERE id = $1 AND {scope.shifted_sql(2)}",
            str(history_id).strip(),
            *scope.params,
        )
        return _deleted_count(result)
    except Exception as exc:
        print(f"[database] failed to delete command history item | error={exc}", flush=True)
        return 0
    finally:
        if connection is not None:
            await connection.close()


async def _ensure_device(connection, *, device_id: Optional[str], language: Optional[str]):
    if not _has_text(device_id):
        return None

    preferred_language = _clean_text(language, uppercase=True)
    if preferred_language is None:
        return await connection.fetchval(
            """
            INSERT INTO devices (
                device_id,
                platform,
                last_seen_at
            )
            VALUES ($1, 'android', now())
            ON CONFLICT (device_id)
            DO UPDATE SET last_seen_at = now()
            RETURNING id
            """,
            str(device_id).strip(),
        )

    return await connection.fetchval(
        """
        INSERT INTO devices (
            device_id,
            platform,
            preferred_language,
            last_seen_at
        )
        VALUES ($1, 'android', $2, now())
        ON CONFLICT (device_id)
        DO UPDATE SET
            preferred_language = EXCLUDED.preferred_language,
            last_seen_at = now()
        RETURNING id
        """,
        str(device_id).strip(),
        preferred_language,
    )


async def _history_scope(connection, *, device_id: Optional[str], session_id: Optional[str]):
    if _has_text(device_id):
        database_device_id = await _ensure_device(connection, device_id=device_id, language=None)
        return _Scope("device_ref_id = $1", [database_device_id])

    if _has_text(session_id):
        return _Scope("session_id = $1", [str(session_id).strip()])

    return None


def _where_clause(scope, query: Optional[str]):
    if not _has_text(query):
        return scope.sql, scope.params

    search = f"%{str(query).strip()}%"
    query_position = len(scope.params) + 1
    return (
        f"{scope.sql} AND (text ILIKE ${query_position} "
        f"OR COALESCE(intent, '') ILIKE ${query_position} "
        f"OR COALESCE(error_code, '') ILIKE ${query_position})",
        [*scope.params, search],
    )


def _row_to_history_item(row) -> Dict[str, Any]:
    return {
        "id": row["id"],
        "text": row["text"],
        "language": row["language"],
        "intent": row["intent"],
        "parameters": _json_dict(row["parameters_json"]),
        "accepted": bool(row["accepted"]),
        "result_status": row["result_status"],
        "error_code": row["error_code"],
        "confidence": _optional_float(row["confidence"]),
        "processing_time_ms": _optional_float(row["processing_time_ms"]),
        "created_at": row["created_at"].isoformat(),
    }


def _json_dict(value) -> Dict[str, Any]:
    if isinstance(value, dict):
        return value
    if isinstance(value, str) and value.strip():
        try:
            parsed = json.loads(value)
            return parsed if isinstance(parsed, dict) else {}
        except json.JSONDecodeError:
            return {}
    return {}


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


def _clean_text(value: Optional[str], *, uppercase: bool = False) -> Optional[str]:
    if not _has_text(value):
        return None

    text = str(value).strip()
    return text.upper() if uppercase else text


def _optional_float(value) -> Optional[float]:
    if value is None:
        return None
    if isinstance(value, Decimal):
        return float(value)
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


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
