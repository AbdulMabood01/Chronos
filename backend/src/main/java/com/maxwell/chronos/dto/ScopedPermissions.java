package com.maxwell.chronos.dto;

import java.util.List;
import java.util.Map;

/** Server-derived permission snapshots for UI use; operations must still authorize the resource. */
public final class ScopedPermissions {
    private ScopedPermissions() {}

    public record Platform(List<String> roles, Map<String, Boolean> capabilities) {}
    public record Company(long companyId, List<String> companyRoles, Map<String, Boolean> capabilities,
                          List<Project> projects) {}
    public record Project(long projectId, List<String> roles, Map<String, Boolean> capabilities) {}
}
