import {LoadingIndicator} from '../components/Hourglass';
import './CompanyManagement.css';
import {useEffect,useState} from 'react';
import {useAuth} from '../AuthContext';
import {useCompany} from '../CompanyContext';
import {companyLeaveAPI,companyAPI} from '../api';
import {formatDate} from '../utils/dates';
import {leaveWorkingDates} from '../utils/federalHolidays';
import LeaveBalancePreview from '../components/LeaveBalancePreview';
import ScreenTitle from '../components/ScreenTitle';
import LeaveBalancePanel from '../components/LeaveBalancePanel';
import './VacationRequests.css';
import './LeavePolicy.css';
const empty={startDate:'',endDate:'',vacationType:'VACATION',specialReason:'',notes:''};
const specialReasons=['Personal','Parental','Maternity','Paternity','Adoption','Jury duty','Military','Family care','Religious','Other'];
const workingDays=(start,end)=>leaveWorkingDates(start,end).length;
const deduction=row=>{
 if(['DRAFT','REJECTED'].includes(row.status))return 'No days deducted';
 if(row.vacationType==='SPECIAL'&&!row.accountingType)return 'Accounting pending: Company Admin will classify this leave';
 const type=row.accountingType||row.vacationType;
 if(type==='PAID_NO_QUOTA')return 'Paid leave: no annual balance deduction';
 if(['UNPAID','UNPAID_LEAVE'].includes(type))return 'Unpaid leave: no annual balance deduction';
 return `${workingDays(row.startDate,row.endDate)} days ${row.status==='SUBMITTED'?'pending against':'deducted from'} ${label(type)}`;
};
const types=['VACATION','SICK','BEREAVEMENT','UNPAID_LEAVE','SPECIAL'];
const label=value=>value.replaceAll('_',' ').toLowerCase().replace(/^./,letter=>letter.toUpperCase());
const message=error=>error?.userMessage||error?.response?.data?.message||'Unable to complete the leave request.';
export default function CompanyLeave({management=false}){
  const {user}=useAuth();const {currentCompany,platformAdmin,companyCapabilities}=useCompany();
  const allowed=currentCompany&&!platformAdmin&&(!management||companyCapabilities?.canManageLeavePolicy);
  const [rows,setRows]=useState([]),[members,setMembers]=useState([]),[member,setMember]=useState('');
  const [loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState(''),[status,setStatus]=useState('');
  const [draft,setDraft]=useState(null),[form,setForm]=useState(empty),[review,setReview]=useState(null),[reason,setReason]=useState(''),[accounting,setAccounting]=useState('');
  const [year,setYear]=useState(new Date().getFullYear()),[preview,setPreview]=useState(null),[revision,setRevision]=useState(0);
  const [managementTab,setManagementTab]=useState('requests'),[requestFilter,setRequestFilter]=useState('SUBMITTED');
  useEffect(()=>{
    let alive=true;setRows([]);setMembers([]);setMember('');setLoading(true);setError('');setDraft(null);setReview(null);setPreview(null);
    if(!allowed){setLoading(false);return;}
    Promise.all([management?companyLeaveAPI.requests(currentCompany.id):companyLeaveAPI.mine(currentCompany.id),management?companyAPI.members(currentCompany.id):Promise.resolve({data:[]})])
      .then(([requests,people])=>{if(alive){setRows((requests.data||[]).filter(row=>String(row.companyId)===String(currentCompany.id)));setMembers((people.data||[]).filter(m=>m.status==='ACTIVE'));}})
      .catch(err=>{if(alive)setError(message(err));}).finally(()=>{if(alive)setLoading(false);});
    return()=>{alive=false;};
  },[currentCompany?.id,allowed,management,revision]);
  const run=async(action,success)=>{setBusy(true);setError('');setStatus('');try{await action();setStatus(success);setRevision(value=>value+1);}catch(err){setError(message(err));}finally{setBusy(false);}};
  if(!allowed)return <div className="page-container"><p role="alert">Company leave access is required.</p></div>;
  const save=event=>{event.preventDefault();run(()=>companyLeaveAPI.save(currentCompany.id,draft.id,{...form,version:draft.version}),'Leave draft saved.');};
  const visibleRows=management?rows.filter(row=>!requestFilter||row.status===requestFilter).sort((a,b)=>Number(b.status==='SUBMITTED')-Number(a.status==='SUBMITTED')):rows;
  return <div className={`page-container vacation-page${management?' company-management-page':''}`}>
    <div className="header-bar"><ScreenTitle title={management?'Leave management':'My leave'} icon="calendar" eyebrow={management?'MANAGEMENT':currentCompany.name} description={management?`Manage company policy, allowances, and requests in ${currentCompany.name}.`:undefined}/>{!management&&<button className="button button-primary" disabled={busy} onClick={()=>{setDraft({id:null});setForm(empty);}}>New leave request</button>}</div>
    {error&&<p className="error-message" role="alert">{error}</p>}{status&&<p className="success-message" role="status">{status}</p>}
    {management&&<nav className="management-section-tabs" aria-label="Leave management sections">{[['requests','Requests'],['allowances','Allowances'],['policy','Policy']].map(([key,title])=><button key={key} type="button" className={`button ${managementTab===key?'button-primary':'button-secondary'}`} aria-pressed={managementTab===key} onClick={()=>{setManagementTab(key);setReview(null);}}>{title}</button>)}</nav>}
    {management?<>
      {managementTab==='policy'&&<section className="card management-section"><h2>Annual company policy</h2><p>Uses this company's saved leave defaults. Personal overrides and extra grants are preserved.</p>
        <label className="management-field">Leave year<input type="number" min="1900" max="9998" value={year} disabled={busy} onChange={event=>{setYear(Number(event.target.value));setPreview(null);}}/></label>
        <button className="button button-secondary" disabled={busy} onClick={async()=>{setBusy(true);setError('');try{setPreview((await companyLeaveAPI.preview(currentCompany.id,year)).data);}catch(err){setError(message(err));}finally{setBusy(false);}}}>Preview policy</button>
        {preview&&<div className="leave-policy-preview" aria-label="Annual policy preview" role="region"><h3>{preview.year} policy preview</h3><p>Annual allowances per member</p><dl><div><dt>Vacation</dt><dd>{preview.vacationDays} days</dd></div><div><dt>Sick leave</dt><dd>{preview.sickDays} days</dd></div><div><dt>Bereavement</dt><dd>{preview.bereavementDays} days</dd></div></dl><h3>Members affected</h3><dl><div><dt>New allowances</dt><dd>{preview.newAllowances}</dd></div><div><dt>Policy allowances updated</dt><dd>{preview.policyAllowances}</dd></div><div><dt>Personal overrides preserved</dt><dd>{preview.overrides}</dd></div></dl><p>Review these totals before applying. Personal overrides and extra grants are preserved.</p><button className="button button-primary" disabled={busy} onClick={()=>run(()=>companyLeaveAPI.apply(currentCompany.id,preview),'Company leave policy applied.')}>Apply {preview.year} policy</button></div>}
      </section>}
      {managementTab==='allowances'&&<section className="card management-section"><h2>Member allowances</h2><p>Choose a company member to view and adjust their leave balance.</p><label className="management-field">Member allowance<select value={member} disabled={busy} onChange={event=>setMember(event.target.value)}><option value="">Choose a company member</option>{members.map(m=><option key={m.user_id} value={m.user_id}>{m.first_name} {m.last_name} ({m.email})</option>)}</select></label>
        {member&&<LeaveBalancePanel key={`${member}:${revision}`} userId={Number(member)} editable/>}
      </section>}
    </>:<div className="card leave-balances-view"><LeaveBalancePanel key={revision} userId={user.id} ownBalance/></div>}
    {!management&&draft&&<form className="card form vacation-editor" onSubmit={save}><h2>{draft.id?'Edit leave draft':'New leave request'}</h2><p aria-live="polite">{workingDays(form.startDate,form.endDate)} working days selected ({workingDays(form.startDate,form.endDate)*8} hours). Weekends and U.S. federal holidays excluded.</p>
      <label className="management-field">Start date<input type="date" required value={form.startDate} disabled={busy} onChange={e=>setForm({...form,startDate:e.target.value})}/></label>
      <label className="management-field">End date<input type="date" required min={form.startDate} value={form.endDate} disabled={busy} onChange={e=>setForm({...form,endDate:e.target.value})}/></label>
      <label className="management-field">Leave type<select value={form.vacationType} disabled={busy} onChange={e=>setForm({...form,vacationType:e.target.value})}>{types.map(type=><option value={type} key={type}>{label(type)}</option>)}</select></label>
      {form.vacationType==='SPECIAL'&&<label className="management-field">Special leave reason<select required value={form.specialReason} disabled={busy} onChange={e=>setForm({...form,specialReason:e.target.value})}><option value="">Choose a reason</option>{[...new Set([...specialReasons,...(form.specialReason?[form.specialReason]:[])])].map(reason=><option key={reason}>{reason}</option>)}</select></label>}
      <label className="management-field">Notes<textarea required={form.vacationType==='SPECIAL'&&form.specialReason==='Other'} maxLength={500} value={form.notes} disabled={busy} onChange={e=>setForm({...form,notes:e.target.value})}/></label>
      <LeaveBalancePreview companyId={currentCompany.id} userId={user.id} form={form} requests={rows} editingId={draft.id}/>
      <button className="button button-primary" disabled={busy}>Save draft</button><button type="button" className="button button-secondary" disabled={busy} onClick={()=>setDraft(null)}>Cancel</button>
    </form>}
    {review&&<section className="card management-section" role="region" aria-label="Review leave request"><h2>{review.userName}: {formatDate(review.startDate)} to {formatDate(review.endDate)}</h2><p>{label(review.vacationType)}: {review.notes||'No notes'}</p>
      {review.vacationType==='SPECIAL'&&<><p>Reason: {review.specialReason}</p><label className="management-field">Count special leave as<select value={accounting} disabled={busy} onChange={e=>setAccounting(e.target.value)}><option value="">Choose accounting</option>{['VACATION','SICK','BEREAVEMENT','PAID_NO_QUOTA','UNPAID'].map(type=><option value={type} key={type}>{label(type)}</option>)}</select></label></>}
      <label className="management-field">Rejection reason<input maxLength={500} value={reason} disabled={busy} onChange={e=>setReason(e.target.value)}/></label>
      <button className="button button-primary" disabled={busy||(review.vacationType==='SPECIAL'&&!accounting)} onClick={()=>run(()=>companyLeaveAPI.decide(currentCompany.id,review.id,true,{version:review.version,accountingType:accounting||null}),'Leave approved.')}>Approve leave</button>
      <button className="button button-secondary" disabled={busy||!reason.trim()} onClick={()=>run(()=>companyLeaveAPI.decide(currentCompany.id,review.id,false,{version:review.version,reason}),'Leave rejected.')}>Reject leave</button>
      <button className="button button-secondary" disabled={busy} onClick={()=>setReview(null)}>Cancel review</button>
    </section>}
    {(!management||managementTab==='requests')&&<section className="leave-history" aria-label={management?'Company leave requests':'Your requests'}>
      {management&&<div className="leave-request-filters"><label className="management-field">Request status<select value={requestFilter} onChange={event=>setRequestFilter(event.target.value)}><option value="SUBMITTED">Pending approval</option><option value="">All requests</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option><option value="DRAFT">Draft</option></select></label></div>}
      <div className="leave-history-heading"><div><span className="vacation-kicker">TIME OFF</span><h2>{management?'Company leave requests':'Your requests'}</h2><p>{management?'Review member requests and their leave balance impact.':'Track your dates, approval status, and leave balance impact.'}</p></div><span className="leave-request-count">{visibleRows.length} requests</span></div>
      {loading?<LoadingIndicator label="Loading leave..."/>:!visibleRows.length?<div className="management-empty"><h3>{management?'No leave requests match this view.':'No leave requests in this company.'}</h3><p>{management?'Choose All requests to view other statuses.':'Leave requests will appear here when submitted.'}</p></div>:<div className="leave-history-list">{visibleRows.map(row=><article className="leave-history-item" key={row.id}>
        <div className="leave-history-main"><div className="leave-history-title"><h3>{label(row.vacationType)}{row.specialReason&&<span> / {row.specialReason}</span>}</h3><span className={`status-badge status-${row.status.toLowerCase()}`}>{row.status==='SUBMITTED'?'Awaiting approval':label(row.status)}</span></div>
          {management&&<p className="leave-history-member">{row.userName}</p>}
          <p className="leave-history-dates">{formatDate(row.startDate)}{row.startDate!==row.endDate&&<> <span>to</span> {formatDate(row.endDate)}</>}</p>
          <p className="leave-history-impact">{deduction(row)}</p>
          {row.rejectionReason&&<p className="leave-history-rejection">Reason: {row.rejectionReason}</p>}
          {(row.notes||row.submittedAt||row.approvedByName)&&<details><summary>View details</summary>{row.notes&&<p>{row.notes}</p>}{row.submittedAt&&<p>Submitted {formatDate(row.submittedAt)}</p>}{row.approvedByName&&<p>Approved by {row.approvedByName}</p>}</details>}
        </div>
        <div className="leave-history-duration"><strong>{workingDays(row.startDate,row.endDate)}</strong><span>working {workingDays(row.startDate,row.endDate)===1?'day':'days'}</span><small>{workingDays(row.startDate,row.endDate)*8} hours</small></div>
        <div className="leave-history-actions">{!management&&row.status==='SUBMITTED'&&<button className="button button-small button-secondary" disabled={busy} onClick={()=>run(()=>companyLeaveAPI.remove(currentCompany.id,row.id,row.version),'Submitted leave request deleted.')}>Delete submitted request</button>}{management?(row.status==='SUBMITTED'&&(row.userId!==user.id?<button className="button button-small button-secondary" disabled={busy} onClick={()=>{setReview(row);setReason('');setAccounting('');}}>Review leave</button>:<span>Another Company Admin must review your request.</span>)):['DRAFT','REJECTED'].includes(row.status)&&<div className="compact-actions">
        <button className="button button-small button-secondary" disabled={busy} onClick={()=>{setDraft(row);setForm({startDate:row.startDate,endDate:row.endDate,vacationType:row.vacationType,specialReason:row.specialReason||'',notes:row.notes||''});}}>Edit leave</button>
        <button className="button button-small button-primary" disabled={busy} onClick={()=>run(()=>companyLeaveAPI.submit(currentCompany.id,row.id,row.version),'Leave submitted.')}>Submit leave</button>
        <button className="button button-small button-secondary" disabled={busy} onClick={()=>run(()=>companyLeaveAPI.remove(currentCompany.id,row.id,row.version),'Leave draft deleted.')}>Delete draft</button>
      </div>}</div>
      </article>)}</div>}
    </section>}
  </div>;
}
