import {useEffect,useState} from 'react';
import {useAuth} from '../AuthContext';
import {useCompany} from '../CompanyContext';
import {companyAPI} from '../api';
export default function DetailCorrectionRequests({userId,kind='PROFILE',allowRequest=true,onChanged}){
 const {user}=useAuth();const {currentCompany,platformAdmin,companyCapabilities}=useCompany();const target=userId??user?.id;const company=currentCompany?.id;
 const [records,setRecords]=useState(null),[reason,setReason]=useState(''),[decisions,setDecisions]=useState({}),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const rows=records&&String(records.company)===String(company)&&String(records.target)===String(target)?records.items:[];
 const setRows=items=>setRecords({company,target,items});
 const admin=companyCapabilities?.canManageCompanyPeople===true;
 async function load(){if(!company||platformAdmin)return;try{const r=await companyAPI.corrections(company,target);setRows(r.data||[]);setError('');}catch(e){setError(e.response?.data?.message||e.userMessage||'Unable to load correction requests.');}}
 useEffect(()=>{let active=true;setRows([]);setReason('');setError('');if(!company||platformAdmin)return;companyAPI.corrections(company,target).then(r=>{if(active)setRows(r.data||[]);}).catch(e=>{if(active)setError(e.userMessage||'Unable to load correction requests.');});return()=>{active=false;};},[company,target,platformAdmin]);
 async function act(action){setBusy(true);setError('');try{await action();setReason('');await load();await onChanged?.();}catch(e){setError(e.response?.data?.message||e.userMessage||'Unable to update correction request.');}finally{setBusy(false);}}
 if(!company||platformAdmin)return null;
 const items=rows.filter(r=>r.kind===kind),pending=items.some(r=>r.status==='PENDING'||r.status==='APPROVED');
 return <section className="detail-corrections" aria-label={kind==='PROFILE'?'Personal profile corrections':'Employment corrections'}>
 <h3>{kind==='PROFILE'?'Personal profile corrections':'Employment corrections'}</h3><p>Explain the correction needed. A different Company Admin can authorize one save; the details then lock again.</p>
 {error&&<p role="alert" className="error-message">{error}</p>}
 {allowRequest&&!pending&&<form onSubmit={e=>{e.preventDefault();act(()=>companyAPI.requestCorrection(company,target,{kind,reason}));}}>
 <label><span className="required-field-label">Correction reason</span><textarea required maxLength={1000} value={reason} disabled={busy} onChange={e=>setReason(e.target.value)}/></label><button type="submit" className="button button-secondary" disabled={busy}>Request correction</button></form>}
 {items.map(r=><article key={r.id}><strong>{r.status.charAt(0)+r.status.slice(1).toLowerCase()}</strong><p>{r.reason}</p>{r.decision_reason&&<p>Decision: {r.decision_reason}</p>}
 {admin&&r.status==='PENDING'&&String(r.requested_by)!==String(user?.id)&&String(target)!==String(user?.id)&&<form onSubmit={e=>{e.preventDefault();act(()=>companyAPI.decideCorrection(company,target,r.id,{approve:true,reason:decisions[r.id]||''}));}}>
 <label><span className="required-field-label">Decision reason</span><textarea required maxLength={1000} value={decisions[r.id]||''} disabled={busy} onChange={e=>setDecisions({...decisions,[r.id]:e.target.value})}/></label>
 <button className="button button-primary" disabled={busy}>Approve one correction</button><button type="button" className="button button-secondary" disabled={busy||!decisions[r.id]?.trim()} onClick={()=>act(()=>companyAPI.decideCorrection(company,target,r.id,{approve:false,reason:decisions[r.id]}))}>Reject correction</button></form>}
 </article>)}
 <button type="button" className="button button-secondary" disabled={busy} onClick={()=>act(async()=>{})}>Refresh requests</button>
 </section>;
}
