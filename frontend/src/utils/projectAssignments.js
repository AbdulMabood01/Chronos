export function hasActiveAssignment(project, userId, today) {
  if (project.isActive === false || (project.status && project.status !== 'ACTIVE')) return false;
  if (!Array.isArray(project.assignments)) return true;
  return project.assignments.some(assignment =>
    String(assignment.userId) === String(userId)
    && assignment.isActive !== false
    && (!assignment.startDate || assignment.startDate <= today)
    && (!assignment.endDate || assignment.endDate >= today));
}
