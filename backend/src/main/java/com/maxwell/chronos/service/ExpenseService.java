package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.Project;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.ProjectAssignmentRepository;
import com.maxwell.chronos.repository.ProjectRepository;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ExpenseService {
    private final JdbcTemplate db;
    private final ProjectRepository projects;
    private final ProjectAssignmentRepository assignments;
    private final UserRepository users;
    private final NotificationService notifications;
    @Value("${chronos.expense.receipt-directory:./private/expense-receipts}")
    private String receiptDirectory;

    public record Input(Long projectId, String category, BigDecimal amount, LocalDate expenseDate, String description) {}
    public record Decision(String status, String comment) {}
    public record Receipt(String name, byte[] bytes) {}

    private User user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> new AccessDeniedException("Sign in required"));
    }
    private Project project(Long id) {
        return projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }
    private boolean reviewer(User user, Project project) {
        return user.isProjectAdmin() || user.isAdmin() ||
                project.getProjectManager() != null && Objects.equals(project.getProjectManager().getId(), user.getId());
    }
    private void requireReviewer(User user, Project project) {
        if (!reviewer(user, project)) throw new AccessDeniedException("Project expense review permission required");
    }
    private void validate(Input input) {
        if (input == null || input.projectId() == null || input.expenseDate() == null || input.amount() == null ||
                input.amount().signum() <= 0 || input.amount().scale() > 2 || input.amount().precision() > 14)
            throw new IllegalArgumentException("Project, positive monetary amount, and expense date are required");
        if (input.category() == null || !Set.of("TRAVEL", "MEALS", "SOFTWARE", "EQUIPMENT", "SUPPLIES", "OTHER").contains(input.category()))
            throw new IllegalArgumentException("Invalid expense category");
        if (input.description() == null || input.description().isBlank() || input.description().length() > 2000 ||
                "OTHER".equals(input.category()) && input.description().trim().length() < 10)
            throw new IllegalArgumentException("Description is required; Other needs at least 10 characters");
        if (input.expenseDate().isAfter(LocalDate.now().plusDays(1)))
            throw new IllegalArgumentException("Expense date cannot be in the future");
    }
    private Map<String,Object> row(Long id) {
        return db.queryForList("SELECT e.*, p.code AS project_code, p.name AS project_name, concat_ws(' ',u.first_name,u.last_name) AS employee_name FROM project_expenses e JOIN projects p ON p.id=e.project_id JOIN users u ON u.id=e.employee_id WHERE e.id=?", id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }
    private void requireView(User user, Map<String,Object> expense) {
        if (!Objects.equals(((Number)expense.get("employee_id")).longValue(), user.getId()) && !reviewer(user, project(((Number)expense.get("project_id")).longValue())))
            throw new AccessDeniedException("Expense access denied");
    }
    public List<Map<String,Object>> mine(String email) {
        return db.queryForList("SELECT e.id,e.project_id,e.employee_id,e.category,e.amount,e.expense_date,e.description,e.receipt_name,e.status,e.submitted_at,e.reviewer_id,e.reviewed_at,e.reviewer_comments,p.code AS project_code,p.name AS project_name FROM project_expenses e JOIN projects p ON p.id=e.project_id WHERE e.employee_id=? ORDER BY e.submitted_at DESC,e.id DESC", user(email).getId());
    }
    public List<Map<String,Object>> projectExpenses(String email, Long projectId) {
        User actor = user(email);
        requireReviewer(actor, project(projectId));
        return db.queryForList("SELECT e.id,e.project_id,e.employee_id,e.category,e.amount,e.expense_date,e.description,e.receipt_name,e.status,e.submitted_at,e.reviewer_id,e.reviewed_at,e.reviewer_comments,p.code AS project_code,p.name AS project_name,concat_ws(' ',u.first_name,u.last_name) AS employee_name FROM project_expenses e JOIN projects p ON p.id=e.project_id JOIN users u ON u.id=e.employee_id WHERE e.project_id=? ORDER BY e.submitted_at DESC,e.id DESC", projectId);
    }
    public List<Map<String,Object>> pending(String email) {
        User actor = user(email);
        if (actor.isAdmin() || actor.isProjectAdmin())
            return db.queryForList("SELECT e.*,p.code AS project_code,concat_ws(' ',u.first_name,u.last_name) AS employee_name FROM project_expenses e JOIN projects p ON p.id=e.project_id JOIN users u ON u.id=e.employee_id WHERE e.status='PENDING_APPROVAL' ORDER BY e.submitted_at");
        return db.queryForList("SELECT e.*,p.code AS project_code,concat_ws(' ',u.first_name,u.last_name) AS employee_name FROM project_expenses e JOIN projects p ON p.id=e.project_id JOIN users u ON u.id=e.employee_id WHERE e.status='PENDING_APPROVAL' AND p.project_manager_id=? ORDER BY e.submitted_at", actor.getId());
    }
    public Map<String,Object> totals(String email, Long projectId) {
        requireReviewer(user(email), project(projectId));
        return db.queryForMap("SELECT p.expense_budget AS budget, COALESCE(sum(e.amount) FILTER (WHERE e.status='APPROVED'),0) AS approved, COALESCE(sum(e.amount) FILTER (WHERE e.status='PENDING_APPROVAL'),0) AS pending, p.expense_budget-COALESCE(sum(e.amount) FILTER (WHERE e.status='APPROVED'),0) AS remaining FROM projects p LEFT JOIN project_expenses e ON e.project_id=p.id WHERE p.id=? GROUP BY p.id", projectId);
    }
    public Map<String,Object> detail(String email, Long id) {
        User actor = user(email);
        Map<String,Object> expense = row(id);
        requireView(actor, expense);
        expense.put("history", db.queryForList("SELECT h.status,h.comment,h.created_at,concat_ws(' ',u.first_name,u.last_name) AS actor_name FROM project_expense_history h JOIN users u ON u.id=h.actor_id WHERE h.expense_id=? ORDER BY h.id", id));
        return expense;
    }
    public Map<String,Object> submit(String email, Input input, MultipartFile file) {
        User actor = user(email);
        validate(input);
        Project project = project(input.projectId());
        if (!Boolean.TRUE.equals(project.getIsActive()) || !assignments.existsByProjectIdAndUserIdAndIsActiveTrue(project.getId(), actor.getId()))
            throw new AccessDeniedException("An active project assignment is required");
        if (file == null) throw new IllegalArgumentException("A receipt or document is required");
        FileData receipt = file == null ? null : saveFile(file);
        if (receipt != null) deleteAfterTransaction(receipt.key(), false);
        try {
            Long id = db.queryForObject("INSERT INTO project_expenses(project_id,employee_id,category,amount,expense_date,description,receipt_key,receipt_name,receipt_type,receipt_size,status) VALUES (?,?,?,?,?,?,?,?,?,?,'PENDING_APPROVAL') RETURNING id", Long.class,
                    project.getId(), actor.getId(), input.category(), input.amount(), input.expenseDate(), input.description().trim(), receipt == null ? null : receipt.key(), receipt == null ? null : receipt.name(), receipt == null ? null : receipt.type(), receipt == null ? null : receipt.size());
            history(id, actor.getId(), "PENDING_APPROVAL", null);
            Long managerId = project.getProjectManager() == null ? null : project.getProjectManager().getId();
            if (managerId != null && !managerId.equals(actor.getId())) notifications.createNotification(managerId, "EXPENSE_SUBMITTED", "Expense awaiting review", project.getCode() + " expense submitted", id, "ProjectExpense");
            return detail(email, id);
        } catch (RuntimeException ex) {
            if (receipt != null) try { Files.deleteIfExists(path(receipt.key())); } catch (IOException ignored) {}
            throw ex;
        }
    }
    public Map<String,Object> resubmit(String email, Long id, Input input, MultipartFile file) {
        User actor = user(email);
        Map<String,Object> old = row(id);
        if (!Objects.equals(((Number)old.get("employee_id")).longValue(), actor.getId())) throw new AccessDeniedException("Expense owner required");
        if (!"CHANGES_REQUESTED".equals(old.get("status"))) throw new IllegalArgumentException("Only expenses with requested changes can be resubmitted");
        validate(input);
        if (!Objects.equals(((Number)old.get("project_id")).longValue(), input.projectId())) throw new IllegalArgumentException("Project cannot be changed");
        if (!assignments.existsByProjectIdAndUserIdAndIsActiveTrue(input.projectId(), actor.getId())) throw new AccessDeniedException("An active project assignment is required");
        FileData receipt = file == null ? null : saveFile(file);
        if (receipt != null) {
            deleteAfterTransaction(receipt.key(), false);
            if (old.get("receipt_key") instanceof UUID previous) deleteAfterTransaction(previous, true);
        }
        try {
            int changed = db.update("UPDATE project_expenses SET category=?,amount=?,expense_date=?,description=?,receipt_key=COALESCE(?,receipt_key),receipt_name=COALESCE(?,receipt_name),receipt_type=COALESCE(?,receipt_type),receipt_size=COALESCE(?,receipt_size),status='PENDING_APPROVAL',submitted_at=now(),reviewer_id=NULL,reviewed_at=NULL,reviewer_comments=NULL,updated_at=now() WHERE id=? AND status='CHANGES_REQUESTED'", input.category(),input.amount(),input.expenseDate(),input.description().trim(),receipt == null ? null : receipt.key(),receipt == null ? null : receipt.name(),receipt == null ? null : receipt.type(),receipt == null ? null : receipt.size(),id);
            if (changed != 1) throw new IllegalArgumentException("Expense status changed; reload and try again");
            history(id,actor.getId(),"PENDING_APPROVAL","Resubmitted");
            Project project = project(input.projectId());
            if (project.getProjectManager() != null && !Objects.equals(project.getProjectManager().getId(),actor.getId())) notifications.createNotification(project.getProjectManager().getId(),"EXPENSE_SUBMITTED","Expense awaiting review",project.getCode() + " expense resubmitted",id,"ProjectExpense");
            return detail(email,id);
        } catch (RuntimeException ex) {
            if (receipt != null) try { Files.deleteIfExists(path(receipt.key())); } catch (IOException ignored) {}
            throw ex;
        }
    }
    public Map<String,Object> decide(String email, Long id, Decision decision) {
        User actor = user(email);
        Map<String,Object> expense = row(id);
        requireReviewer(actor, project(((Number)expense.get("project_id")).longValue()));
        if (!"PENDING_APPROVAL".equals(expense.get("status"))) throw new IllegalArgumentException("Expense is not pending approval");
        if (decision == null || decision.status() == null || !Set.of("APPROVED","REJECTED","CHANGES_REQUESTED").contains(decision.status())) throw new IllegalArgumentException("Invalid review decision");
        if (!"APPROVED".equals(decision.status()) && (decision.comment() == null || decision.comment().isBlank())) throw new IllegalArgumentException("A reviewer comment is required");
        if (decision.comment() != null && decision.comment().length() > 2000) throw new IllegalArgumentException("Reviewer comment is too long");
        int changed = db.update("UPDATE project_expenses SET status=?,reviewer_id=?,reviewed_at=now(),reviewer_comments=?,updated_at=now() WHERE id=? AND status='PENDING_APPROVAL'", decision.status(),actor.getId(),decision.comment(),id);
        if (changed != 1) throw new IllegalArgumentException("Expense status changed; reload and try again");
        history(id,actor.getId(),decision.status(),decision.comment());
        Long employeeId = ((Number)expense.get("employee_id")).longValue();
        if (!employeeId.equals(actor.getId())) notifications.createNotification(employeeId,"EXPENSE_REVIEWED","Expense " + decision.status().toLowerCase().replace('_',' '),"Your expense has been reviewed",id,"ProjectExpense");
        return detail(email,id);
    }
    private void history(Long id, Long actor, String status, String comment) {
        db.update("INSERT INTO project_expense_history(expense_id,actor_id,status,comment) VALUES (?,?,?,?)",id,actor,status,comment);
    }
    private record FileData(UUID key, String name, String type, long size) {}
    private Path path(UUID key) { return Path.of(receiptDirectory).toAbsolutePath().normalize().resolve(key.toString()); }
    private void deleteAfterTransaction(UUID key, boolean onCommit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if ((status == STATUS_COMMITTED) == onCommit) {
                    try { Files.deleteIfExists(path(key)); } catch (IOException ignored) {}
                }
            }
        });
    }
    private FileData saveFile(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 10L*1024*1024) throw new IllegalArgumentException("Receipt must be nonempty and at most 10 MB");
        String name = Objects.toString(file.getOriginalFilename(), "receipt").replace('\\','/');
        name = name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_");
        if (name.length() > 200 || !name.matches("(?i).+\\.(pdf|png|jpg|jpeg|webp)")) throw new IllegalArgumentException("Receipt must be PDF, PNG, JPG or WebP");
        String type = Objects.toString(file.getContentType(), "");
        try {
            byte[] bytes = file.getBytes();
            boolean pdf = bytes.length >= 5 && new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
            boolean png = bytes.length >= 8 && bytes[0] == (byte)0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            boolean jpg = bytes.length >= 3 && bytes[0] == (byte)0xff && bytes[1] == (byte)0xd8 && bytes[2] == (byte)0xff;
            boolean webp = bytes.length >= 12 && new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            String ext = name.substring(name.lastIndexOf('.')+1).toLowerCase();
            if (!(pdf && ext.equals("pdf") && type.equals("application/pdf") || png && ext.equals("png") && type.equals("image/png") || jpg && Set.of("jpg","jpeg").contains(ext) && type.equals("image/jpeg") || webp && ext.equals("webp") && type.equals("image/webp")))
                throw new IllegalArgumentException("Receipt file contents do not match its type");
            Path dir = Path.of(receiptDirectory).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            UUID key = UUID.randomUUID();
            Files.write(path(key), bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            return new FileData(key,name,type,bytes.length);
        } catch (IOException e) { throw new IllegalStateException("Unable to store receipt",e); }
    }
    public Receipt receipt(String email, Long id) {
        User actor = user(email);
        Map<String,Object> expense = row(id);
        requireView(actor,expense);
        if (expense.get("receipt_key") == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Receipt not found");
        try { return new Receipt((String)expense.get("receipt_name"),Files.readAllBytes(path((UUID)expense.get("receipt_key")))); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Receipt not found"); }
    }
}
