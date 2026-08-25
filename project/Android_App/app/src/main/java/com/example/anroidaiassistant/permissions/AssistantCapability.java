package com.example.anroidaiassistant.permissions;

public enum AssistantCapability {
    MICROPHONE(true, AccessType.RUNTIME_PERMISSION),
    ACCESSIBILITY_SERVICE(true, AccessType.SERVICE),
    POPUP(true, AccessType.SPECIAL_ACCESS),
    CONTACTS(false, AccessType.RUNTIME_PERMISSION),
    DIRECT_CALL(false, AccessType.RUNTIME_PERMISSION),
    PHONE_STATE(false, AccessType.RUNTIME_PERMISSION),
    ANSWER_CALL(false, AccessType.RUNTIME_PERMISSION),
    CAMERA(false, AccessType.RUNTIME_PERMISSION),
    SOUND_MODE(false, AccessType.SPECIAL_ACCESS),
    SYSTEM_SETTINGS(false, AccessType.SPECIAL_ACCESS);

    private final boolean core;
    private final AccessType accessType;

    AssistantCapability(boolean core, AccessType accessType) {
        this.core = core;
        this.accessType = accessType;
    }

    public boolean isCore() {
        return core;
    }

    public boolean isRuntimePermission() {
        return accessType == AccessType.RUNTIME_PERMISSION;
    }

    public boolean isSpecialAccess() {
        return accessType == AccessType.SPECIAL_ACCESS;
    }

    private enum AccessType {
        RUNTIME_PERMISSION,
        SPECIAL_ACCESS,
        SERVICE
    }
}
