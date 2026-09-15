const fs = require('node:fs');
const path = require('node:path');

// Dependency-free PDF generation. All text uses built-in PDF Helvetica fonts.
const weeks = [
  {
    start: '2026-08-02', end: '2026-08-08', title: 'Source discovery and reporting requirements',
    summary: 'I focused on understanding how Chronos records employee time, project assignments, leave, and approvals. I organized the reporting requirements around traceable project-level hours rather than relying only on monthly totals.',
    completed: [
      'Mapped the reporting relationships between users, projects, assignments, time entries, sessions, and project submissions.',
      'Defined the primary reporting grain as one employee, project, and calendar month, with daily entries retained for reconciliation.',
      'Outlined the audiences for operational reporting: project managers, project operations, and Super Admin reviewers.',
      'Identified sensitive profile fields to exclude from general analytics, including date of birth and SSN-related information.'
    ],
    progress: 'I am refining a source-to-report inventory and separating staffing, timekeeping, and approval metrics so that joins do not inflate totals.',
    risk: 'A monthly timesheet can span several projects. Joining its total directly to project assignments would duplicate hours. Reporting must aggregate daily entries by project first.',
    next: 'Define shared metric definitions and a reconciliation checklist for daily entries, project submissions, and monthly totals.',
    outputs: 'Source inventory outline; reporting-grain definition; sensitive-field exclusion list.'
  },
  {
    start: '2026-08-09', end: '2026-08-15', title: 'Metric definitions and data-quality design',
    summary: 'I concentrated on consistent definitions for logged, submitted, approved, and remaining hours. I translated time-entry rules into a practical data-quality checklist for future reporting validation.',
    completed: [
      'Specified separate measures for recorded hours and approval-qualified hours, preventing submitted time from appearing as approved billing.',
      'Outlined reconciliation from session duration to daily entries, then to employee-project submissions and monthly summaries.',
      'Documented checks for invalid assignment dates, missing project references, negative hours, and daily totals exceeding 24 hours.',
      'Separated a true zero-hour result from missing or incomplete reporting data in the reporting specification.'
    ],
    progress: 'I am defining exception categories so operations can distinguish incorrect source records from reporting calculation issues.',
    risk: 'Assignment-level planned hours and monthly hour plans may represent different allocation periods. Remaining-hour calculations need an agreed precedence and time horizon.',
    next: 'Design the incremental refresh process and historical handling for assignment and approval changes.',
    outputs: 'Metric glossary outline; reconciliation rules; data-quality exception categories.'
  },
  {
    start: '2026-08-16', end: '2026-08-22', title: 'Reporting model and refresh strategy',
    summary: 'I developed the logical approach for a reporting layer that preserves project-level detail and can be refreshed repeatedly without duplicate records. I kept the design aligned with the existing PostgreSQL application.',
    completed: [
      'Outlined employee, project, and assignment dimensions alongside daily-hours and project-submission reporting facts.',
      'Defined stable source identifiers for upserts and a refresh watermark approach for records that change after initial extraction.',
      'Included deleted entries and reopened submissions in the refresh design through periodic source reconciliation.',
      'Specified load logging for refresh start and end times, extracted records, rejected records, and reconciliation differences.'
    ],
    progress: 'I am refining historical snapshots for reporting periods while keeping current operational views responsive to corrections.',
    risk: 'Timestamp-based extraction alone can miss deleted records and some relationship changes. A reconciliation pass is required before treating a reporting period as complete.',
    next: 'Define approval-time rate handling and a repeatable monthly billing reconciliation process.',
    outputs: 'Logical reporting model; incremental-load design; refresh monitoring specification.'
  },
  {
    start: '2026-08-23', end: '2026-08-29', title: 'Approved-hours and billing reconciliation',
    summary: 'I focused on historical billing accuracy and the distinction between current assignment rates and rates captured when hours are approved. I outlined controls for reliable monthly project reporting.',
    completed: [
      'Defined billing calculations at employee-project submission level using approved hours and the approval-time billing rate.',
      'Documented why subsequent assignment-rate changes must not silently alter previously approved reporting amounts.',
      'Outlined report comparisons across project totals, employee totals, and exported timesheet detail.',
      'Specified exceptions for missing approval-rate snapshots and kept client billing rates distinct from employee payroll rates.'
    ],
    progress: 'I am documenting how an approved timesheet reopening should affect reporting snapshots, prior exports, and adjustment tracking.',
    risk: 'A reopened submission can change an earlier approved result. A reporting cutoff and revision policy need business confirmation before month-end reports become reproducible.',
    next: 'Extend quality checks to approved vacation conflicts and define approval-queue performance measures.',
    outputs: 'Approved-hours reporting rules; rate-history controls; month-end reconciliation checklist.'
  },
  {
    start: '2026-08-30', end: '2026-09-05', title: 'Leave consistency and approval visibility',
    summary: 'I expanded the reporting scope to leave conflicts and pending approvals. My focus was making operational exceptions visible without changing the application\'s transactional approval rules.',
    completed: [
      'Outlined checks for billable entries on approved vacation dates and identified submitted or finalized hours as a separate conflict category.',
      'Defined pending approval measures by employee, project, submission date, current status, and responsible reviewer.',
      'Documented routing differences between employee submissions and the project manager\'s own submissions.',
      'Separated current queue age from full approval-cycle duration, which requires rejection and resubmission history.'
    ],
    progress: 'I am refining a management dashboard specification covering hours awaiting review, rejected submissions, and reporting exceptions.',
    risk: 'Current status and latest timestamps cannot fully reconstruct repeated approval cycles. Historical events need to be evaluated before publishing end-to-end turnaround metrics.',
    next: 'Review report-export eligibility, assignment offboarding, and access boundaries for reporting datasets.',
    outputs: 'Leave-conflict checks; approval-aging metric definitions; reviewer-routing reporting notes.'
  },
  {
    start: '2026-09-06', end: '2026-09-12', title: 'Export controls and reporting readiness',
    summary: 'I reviewed reporting readiness across approved project submissions, assignment history, and export access. I used the project task board as a planning input while treating current service rules as the authority for reporting behavior.',
    completed: [
      'Prepared a validation matrix for approved or locked project submissions, including employee, project, period, daily hours, and approval details.',
      'Outlined preservation of historical employee-project rows when assignments become inactive or projects are archived.',
      'Documented reporting access for Admin and Super Admin users and project-scoped visibility for assigned reviewers.',
      'Identified task-board descriptions that need alignment with current role rules before being reused in reporting documentation.'
    ],
    progress: 'I am organizing the acceptance checklist for source-to-export reconciliation and reporting access review with application owners.',
    risk: 'A task-board item marked completed is not evidence of reporting validation against a live dataset. Export accuracy and access behavior still require representative execution evidence.',
    next: 'Prioritize outstanding acceptance decisions and prepare the next reporting validation cycle.',
    outputs: 'Export validation matrix; historical-assignment reporting notes; access-review checklist.'
  },
  {
    start: '2026-09-13', end: '2026-09-14', title: 'Week-to-date review and next-cycle planning',
    summary: 'For this partial reporting week, I consolidated the data-engineering priorities and outstanding acceptance decisions. I focused on the work needed to move from reporting specifications to evidence-backed operational reporting.',
    completed: [
      'Prioritized the open definitions for allocation period, approval-rate fallback, and the treatment of reopened reporting periods.',
      'Organized the next validation pass around multi-project employees, rate changes, vacation conflicts, and offboarded assignments.',
      'Separated implementation tasks from business-policy decisions so each item has an appropriate review owner.',
      'Documented that production load results, reconciliation pass rates, and performance measurements remain unverified.'
    ],
    progress: 'I am preparing the validation sequence: confirm definitions, obtain representative data, implement reporting queries, reconcile outputs, and review exceptions.',
    risk: 'The key dependencies are an approved reporting policy and authorized representative source data. Production-readiness claims should wait for measured validation results.',
    next: 'During September 15-19, confirm the outstanding definitions and begin validating reporting calculations with application and operations owners.',
    outputs: 'Prioritized acceptance backlog; validation sequence; reporting decision register outline.'
  }
];

const esc = s => s.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
function wrap(text, limit) {
  const lines = []; let line = '';
  for (const word of text.split(/\s+/)) {
    if (line && (line + ' ' + word).length > limit) { lines.push(line); line = word; }
    else line += (line ? ' ' : '') + word;
  }
  if (line) lines.push(line);
  return lines;
}
function makePdf(w, index) {
  const commands = []; let y = 623;
  const text = (str, x, yy, size = 10, bold = false, color = '0.16 0.21 0.28') => {
    commands.push(`BT /${bold ? 'F2' : 'F1'} ${size} Tf ${color} rg 1 0 0 1 ${x} ${yy} Tm (${esc(str)}) Tj ET`);
  };
  commands.push('0.08 0.16 0.25 rg 0 690 612 102 re f');
  text('MAXWELL SOFTWARE AND SOLUTIONS', 44, 760, 12, true, '1 1 1');
  text('Weekly Status Report', 44, 728, 25, true, '1 1 1');
  text('CHRONOS  /  DATA ENGINEERING', 44, 707, 10, false, '0.72 0.85 0.92');
  text('Abdul Majeed', 44, 667, 13, true);
  text(`WEEK ${String(index + 1).padStart(2, '0')}  |  ${w.start} to ${w.end}`, 285, 667, 10, true);
  text('Scenario-based activities grounded in Chronos; not verified personal work history.', 44, 647, 8);
  function section(label, body, bullet = false) {
    text(label.toUpperCase(), 44, y, 9, true, '0.02 0.40 0.46'); y -= 17;
    const items = Array.isArray(body) ? body : [body];
    for (const item of items) {
      const lines = wrap(item, bullet ? 96 : 99);
      lines.forEach((line, i) => { text((bullet && i === 0 ? '- ' : '') + line, bullet ? 49 : 44, y, 9.5); y -= 12.5; });
      if (bullet) y -= 3;
    }
    y -= 11;
  }
  section(w.title, w.summary);
  section('Work completed', w.completed, true);
  section('Work in progress', w.progress);
  section('Risks and dependencies', w.risk);
  section(index === 6 ? 'Plan for the rest of the week' : 'Next week', w.next);
  section('Outputs covered', w.outputs);
  if (y < 83) throw Error(`Page content overflow: week ${index + 1}, y=${y}`);
  const stream = commands.join('\n');
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>',
    `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`,
    `<< /Title (${esc('Chronos Weekly Status Report: ' + w.start + ' to ' + w.end)}) /Author (Abdul Majeed) /Subject (Scenario-based data engineering status report) >>`
  ];
  let pdf = '%PDF-1.4\n'; const offsets = [0];
  objects.forEach((obj, i) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${i + 1} 0 obj\n${obj}\nendobj\n`; });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets.slice(1)) pdf += `${String(offset).padStart(10, '0')} 00000 n \n`;
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R /Info 7 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return pdf;
}

weeks.forEach((w, i) => {
  const filename = `Abdul_Majeed_Weekly_Status_${w.start}_to_${w.end}.pdf`;
  const pdf = makePdf(w, i);
  fs.writeFileSync(path.join(__dirname, filename), pdf);
  // Validate every cross-reference offset against its object header.
  const entries = [...pdf.matchAll(/^(\d{10}) 00000 n /gm)];
  entries.forEach((entry, n) => {
    if (!pdf.slice(Number(entry[1])).startsWith(`${n + 1} 0 obj`)) throw Error('Invalid PDF cross-reference');
  });
  console.log(`Created and checked ${filename} (${Buffer.byteLength(pdf)} bytes)`);
});
fs.writeFileSync(path.join(__dirname, 'report-content.json'), JSON.stringify(weeks, null, 2) + '\n');
