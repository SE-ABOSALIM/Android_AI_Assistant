from dataclasses import dataclass

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from V3.database.installation_auth_repository import find_installation_by_credential


_bearer_scheme = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class AuthenticatedInstallation:
    device_ref_id: str
    device_id: str


async def require_authenticated_installation(
    credentials: HTTPAuthorizationCredentials = Depends(_bearer_scheme),
) -> AuthenticatedInstallation:
    if (
        credentials is None
        or credentials.scheme.lower() != "bearer"
        or not credentials.credentials.strip()
    ):
        raise _unauthorized()

    row = await find_installation_by_credential(credentials.credentials.strip())
    if row is None:
        raise _unauthorized()

    return AuthenticatedInstallation(
        device_ref_id=str(row["device_ref_id"]),
        device_id=str(row["device_id"]),
    )


def require_owned_device(
    identity: AuthenticatedInstallation,
    claimed_device_id: str | None,
) -> str:
    if claimed_device_id is not None and claimed_device_id.strip() != identity.device_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Requested resource is not available for this installation",
        )
    return identity.device_id


def _unauthorized() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Not authenticated",
        headers={"WWW-Authenticate": "Bearer"},
    )
