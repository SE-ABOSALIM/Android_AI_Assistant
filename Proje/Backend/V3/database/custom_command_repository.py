import json
from typing import Any, Dict, List, Optional

from V3.database.connection import is_database_configured, open_database_connection
from V3.utils.text import normalized_lower


SUPPORTED_CUSTOM_STEP_INTENTS = {
    "OPEN_APP",
    "SEARCH_QUERY",
    "CLICK_ITEM",
    "SHOW_LABELS",
    "WAIT",
}

ARABIC_NAME_VARIANT_MAP = str.maketrans(
    {
        "\u0622": "\u0627",
        "\u0623": "\u0627",
        "\u0625": "\u0627",
        "\u0671": "\u0627",
        "\u0629": "\u0647",
        "\u0649": "\u064a",
        "\u0626": "\u064a",
        "\u0624": "\u0648",
    }
)

ARABIC_INDIC_DIGIT_MAP = str.maketrans(
    "\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669"
    "\u06F0\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9",
    "01234567890123456789",
)


async def list_custom_commands(*, device_id: Optional[str], language: Optional[str]) -> Dict[str, Any]:
    if not is_database_configured() or not _has_text(device_id):
        return {"items": []}

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return {"items": []}

        database_device_id = await _ensure_device(connection, device_id=device_id, language=language)
        rows = await connection.fetch(
            """
            SELECT
                id::text,
                name,
                language,
                enabled,
                created_at,
                updated_at
            FROM custom_commands
            WHERE device_ref_id = $1
              AND language = $2
            ORDER BY updated_at DESC, created_at DESC
            """,
            database_device_id,
            _normalize_language(language),
        )
        return {"items": [await _command_from_row(connection, row) for row in rows]}
    except Exception as exc:
        print(f"[database] failed to list custom commands | error={exc}", flush=True)
        return {"items": []}
    finally:
        if connection is not None:
            await connection.close()


async def save_custom_command(
    *,
    device_id: Optional[str],
    language: Optional[str],
    name: str,
    steps: List[Dict[str, Any]],
) -> Optional[Dict[str, Any]]:
    if not is_database_configured() or not _has_text(device_id) or not _has_text(name):
        return None

    clean_steps = _clean_steps(steps)
    if not clean_steps:
        return None

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return None

        async with connection.transaction():
            database_device_id = await _ensure_device(connection, device_id=device_id, language=language)
            command_id = await connection.fetchval(
                """
                INSERT INTO custom_commands (
                    device_ref_id,
                    name,
                    normalized_name,
                    language,
                    updated_at
                )
                VALUES ($1, $2, $3, $4, now())
                ON CONFLICT (device_ref_id, language, normalized_name)
                DO UPDATE SET
                    name = EXCLUDED.name,
                    enabled = true,
                    updated_at = now()
                RETURNING id
                """,
                database_device_id,
                str(name).strip(),
                _normalize_name(name, language),
                _normalize_language(language),
            )
            await _replace_steps(connection, command_id=command_id, steps=clean_steps)

        return await get_custom_command(command_id=str(command_id), device_id=device_id)
    except Exception as exc:
        print(f"[database] failed to save custom command | error={exc}", flush=True)
        return None
    finally:
        if connection is not None:
            await connection.close()


async def update_custom_command(
    *,
    command_id: str,
    device_id: Optional[str],
    language: Optional[str],
    name: str,
    steps: List[Dict[str, Any]],
) -> Optional[Dict[str, Any]]:
    if not is_database_configured() or not _has_text(command_id) or not _has_text(device_id) or not _has_text(name):
        return None

    clean_steps = _clean_steps(steps)
    if not clean_steps:
        return None

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return None

        async with connection.transaction():
            database_device_id = await _ensure_device(connection, device_id=device_id, language=language)
            updated_id = await connection.fetchval(
                """
                UPDATE custom_commands
                   SET name = $3,
                       normalized_name = $4,
                       language = $5,
                       updated_at = now()
                 WHERE id = $1::uuid
                   AND device_ref_id = $2
                 RETURNING id
                """,
                str(command_id).strip(),
                database_device_id,
                str(name).strip(),
                _normalize_name(name, language),
                _normalize_language(language),
            )
            if updated_id is None:
                return None
            await _replace_steps(connection, command_id=updated_id, steps=clean_steps)

        return await get_custom_command(command_id=command_id, device_id=device_id)
    except Exception as exc:
        print(f"[database] failed to update custom command | error={exc}", flush=True)
        return None
    finally:
        if connection is not None:
            await connection.close()


async def delete_custom_command(*, command_id: str, device_id: Optional[str]) -> int:
    if not is_database_configured() or not _has_text(command_id) or not _has_text(device_id):
        return 0

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return 0

        database_device_id = await _ensure_device(connection, device_id=device_id, language=None)
        result = await connection.execute(
            """
            DELETE FROM custom_commands
             WHERE id = $1::uuid
               AND device_ref_id = $2
            """,
            str(command_id).strip(),
            database_device_id,
        )
        return _deleted_count(result)
    except Exception as exc:
        print(f"[database] failed to delete custom command | error={exc}", flush=True)
        return 0
    finally:
        if connection is not None:
            await connection.close()


async def get_custom_command(*, command_id: str, device_id: Optional[str]) -> Optional[Dict[str, Any]]:
    if not is_database_configured() or not _has_text(command_id) or not _has_text(device_id):
        return None

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return None

        database_device_id = await _ensure_device(connection, device_id=device_id, language=None)
        row = await connection.fetchrow(
            """
            SELECT
                id::text,
                name,
                language,
                enabled,
                created_at,
                updated_at
            FROM custom_commands
            WHERE id = $1::uuid
              AND device_ref_id = $2
            """,
            str(command_id).strip(),
            database_device_id,
        )
        if row is None:
            return None
        return await _command_from_row(connection, row)
    except Exception as exc:
        print(f"[database] failed to get custom command | error={exc}", flush=True)
        return None
    finally:
        if connection is not None:
            await connection.close()


async def find_custom_command_by_spoken_name(
    *,
    device_id: Optional[str],
    language: Optional[str],
    spoken_name: str,
) -> Optional[Dict[str, Any]]:
    if not is_database_configured() or not _has_text(device_id) or not _has_text(spoken_name):
        return None

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return None

        database_device_id = await _ensure_device(connection, device_id=device_id, language=language)
        normalized_language = _normalize_language(language)
        row = await connection.fetchrow(
            """
            SELECT
                id::text,
                name,
                language,
                enabled,
                created_at,
                updated_at
            FROM custom_commands
            WHERE device_ref_id = $1
              AND language = $2
              AND normalized_name = $3
              AND enabled = true
            """,
            database_device_id,
            normalized_language,
            _normalize_name(spoken_name, normalized_language),
        )
        if row is None:
            row = await _find_relaxed_custom_command_row(
                connection,
                database_device_id=database_device_id,
                language=normalized_language,
                spoken_name=spoken_name,
            )
        if row is None:
            return None
        return await _command_from_row(connection, row)
    except Exception as exc:
        print(f"[database] failed to find custom command | error={exc}", flush=True)
        return None
    finally:
        if connection is not None:
            await connection.close()


async def _command_from_row(connection, row) -> Dict[str, Any]:
    steps = await connection.fetch(
        """
        SELECT
            step_order,
            intent,
            parameters_json,
            wait_after_ms,
            stop_on_failure
        FROM custom_command_steps
        WHERE custom_command_id = $1::uuid
        ORDER BY step_order ASC
        """,
        row["id"],
    )

    return {
        "id": str(row["id"]),
        "name": row["name"],
        "language": row["language"],
        "enabled": bool(row["enabled"]),
        "steps": [_step_from_row(step) for step in steps],
        "created_at": row["created_at"].isoformat(),
        "updated_at": row["updated_at"].isoformat(),
    }


async def _find_relaxed_custom_command_row(
    connection,
    *,
    database_device_id,
    language: str,
    spoken_name: str,
):
    if language != "AR":
        return None

    rows = await connection.fetch(
        """
        SELECT
            id::text,
            name,
            language,
            enabled,
            created_at,
            updated_at
        FROM custom_commands
        WHERE device_ref_id = $1
          AND language = $2
          AND enabled = true
        """,
        database_device_id,
        language,
    )
    if not rows:
        return None

    normalized_spoken_name = _normalize_name_for_matching(spoken_name, language)
    exact_matches = [
        row
        for row in rows
        if _normalize_name_for_matching(row["name"], language) == normalized_spoken_name
    ]
    if len(exact_matches) == 1:
        return exact_matches[0]

    close_matches = [
        row
        for row in rows
        if _edit_distance_at_most_one(
            _normalize_name_for_matching(row["name"], language),
            normalized_spoken_name,
        )
    ]
    return close_matches[0] if len(close_matches) == 1 else None


async def _replace_steps(connection, *, command_id, steps: List[Dict[str, Any]]) -> None:
    await connection.execute(
        "DELETE FROM custom_command_steps WHERE custom_command_id = $1",
        command_id,
    )
    for index, step in enumerate(steps, start=1):
        await connection.execute(
            """
            INSERT INTO custom_command_steps (
                custom_command_id,
                step_order,
                intent,
                parameters_json,
                wait_after_ms,
                stop_on_failure
            )
            VALUES ($1, $2, $3, $4::jsonb, $5, $6)
            """,
            command_id,
            int(step.get("step_order") or index),
            step["intent"],
            json.dumps(step["parameters"], ensure_ascii=False),
            int(step.get("wait_after_ms") or 0),
            bool(step.get("stop_on_failure", True)),
        )


async def _ensure_device(connection, *, device_id: Optional[str], language: Optional[str]):
    if not _has_text(device_id):
        return None

    normalized_language = _normalize_language(language) if _has_text(language) else None
    if normalized_language is None:
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
            preferred_language = COALESCE(EXCLUDED.preferred_language, devices.preferred_language),
            last_seen_at = now()
        RETURNING id
        """,
        str(device_id).strip(),
        normalized_language,
    )


def _clean_steps(steps: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    result: List[Dict[str, Any]] = []
    for raw_step in steps or []:
        intent = str((raw_step or {}).get("intent") or "").strip().upper()
        if intent not in SUPPORTED_CUSTOM_STEP_INTENTS:
            continue

        parameters = raw_step.get("parameters") or {}
        if not isinstance(parameters, dict):
            parameters = {}

        parameters = _clean_parameters(intent, parameters)
        if intent != "SHOW_LABELS" and not parameters:
            continue

        result.append(
            {
                "step_order": len(result) + 1,
                "intent": intent,
                "parameters": parameters,
                "wait_after_ms": _wait_after_ms(raw_step, parameters),
                "stop_on_failure": bool(raw_step.get("stop_on_failure", True)),
            }
        )
    return result


def _clean_parameters(intent: str, parameters: Dict[str, Any]) -> Dict[str, Any]:
    if intent == "OPEN_APP":
        return _single_text_param(parameters, "app_name")
    if intent == "SEARCH_QUERY":
        return _single_text_param(parameters, "query")
    if intent == "CLICK_ITEM":
        return _single_text_param(parameters, "target_text")
    if intent == "WAIT":
        return _single_text_param(parameters, "duration_ms")
    if intent == "SHOW_LABELS":
        return _optional_text_param(parameters, "label_number")
    return {}


def _wait_after_ms(raw_step: Dict[str, Any], parameters: Dict[str, Any]) -> int:
    direct_value = _parse_non_negative_int((raw_step or {}).get("wait_after_ms"))
    if direct_value:
        return direct_value

    return _parse_non_negative_int((parameters or {}).get("duration_ms")) or 0


def _parse_non_negative_int(value: Any) -> Optional[int]:
    if value is None:
        return None
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return max(0, int(value))

    text = str(value).strip().translate(ARABIC_INDIC_DIGIT_MAP)
    if not text:
        return None

    if text.startswith("-"):
        return 0

    digits = "".join(char for char in text if char.isdigit())
    if not digits:
        return None
    return max(0, int(digits))


def _single_text_param(parameters: Dict[str, Any], key: str) -> Dict[str, Any]:
    value = parameters.get(key)
    if not _has_text(value):
        return {}
    return {key: str(value).strip()}


def _optional_text_param(parameters: Dict[str, Any], key: str) -> Dict[str, Any]:
    value = parameters.get(key)
    if not _has_text(value):
        return {}
    return {key: str(value).strip()}


def _step_from_row(row) -> Dict[str, Any]:
    parameters = row["parameters_json"]
    if isinstance(parameters, str):
        try:
            parameters = json.loads(parameters)
        except json.JSONDecodeError:
            parameters = {}

    return {
        "step_order": int(row["step_order"]),
        "intent": row["intent"],
        "parameters": parameters or {},
        "wait_after_ms": int(row["wait_after_ms"] or 0),
        "stop_on_failure": bool(row["stop_on_failure"]),
    }


def _normalize_language(language: Optional[str]) -> str:
    language = str(language or "TR").strip().upper()
    return language if language in {"EN", "TR", "AR"} else "TR"


def _normalize_name(value: str, language: Optional[str]) -> str:
    return _normalize_name_for_matching(value, _normalize_language(language))


def _normalize_name_for_matching(value: str, language: Optional[str]) -> str:
    normalized = normalized_lower(value)
    if _normalize_language(language) != "AR":
        return normalized
    return normalized.replace("\u0640", "").translate(ARABIC_NAME_VARIANT_MAP)


def _edit_distance_at_most_one(left: str, right: str) -> bool:
    if left == right:
        return True

    left_length = len(left)
    right_length = len(right)
    if abs(left_length - right_length) > 1:
        return False

    differences = 0
    left_index = 0
    right_index = 0
    while left_index < left_length and right_index < right_length:
        if left[left_index] == right[right_index]:
            left_index += 1
            right_index += 1
            continue

        differences += 1
        if differences > 1:
            return False

        if left_length == right_length:
            left_index += 1
            right_index += 1
        elif left_length > right_length:
            left_index += 1
        else:
            right_index += 1

    return True


def _has_text(value: Any) -> bool:
    return value is not None and str(value).strip() != ""


def _deleted_count(result: str) -> int:
    try:
        return int(str(result).split()[-1])
    except (IndexError, ValueError):
        return 0
