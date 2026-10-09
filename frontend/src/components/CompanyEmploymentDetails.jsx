import {useEffect,useState} from 'react';
import {useAuth} from '../AuthContext';
import {useCompany} from '../CompanyContext';
import {companyAPI} from '../api';
import './EmploymentDetails.css';

const fields=data=>({employeeId:data.employeeId||'',jobTitle:data.jobTitle||'',joiningDate:data.joiningDate||''});
const errorText=error=>error?.response?.data?.message||error?.userMessage||'Unable to load employment details.';
export default function CompanyEmploymentDetails({userId,editable=false,onSaved}) {
  const {user}=useAuth();
  const {currentCompany,platformAdmin,companyCapabilities}=useCompany();
  const targetId=userId??user?.id;
  const [data,setData]=useState(null),[form,setForm]=useState({employeeId:'',jobTitle:'',joiningDate:''});
  const [loading,setLoading]=useState(true),[saving,setSaving]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
  const [attempt,setAttempt]=useState(0);
  useEffect(()=>{
    let cancelled=false;setData(null);setLoading(true);setError('');setMessage('');
    if(!currentCompany||platformAdmin){setLoading(false);return;}
    companyAPI.employment(currentCompany.id,targetId).then(({data:record})=>{
      if(!cancelled){setData(record);setForm(fields(record));}
    }).catch(error=>{if(!cancelled)setError(errorText(error));}).finally(()=>{if(!cancelled)setLoading(false);});
    return()=>{cancelled=true;};
  },[currentCompany?.id,targetId,platformAdmin,attempt]);
  if(!currentCompany||platformAdmin)return null;
  const record=data&&String(data.companyId)===String(currentCompany.id)&&String(data.userId)===String(targetId)?data:null;
  const canEdit=editable&&companyCapabilities?.canManageCompanyPeople===true&&record?.membershipStatus==='ACTIVE';
  const save=async event=>{
    event.preventDefault();setSaving(true);setError('');setMessage('');
    try{
      const response=await companyAPI.updateEmployment(currentCompany.id,targetId,{employeeId:form.employeeId.trim()||null,
        jobTitle:form.jobTitle.trim()||null,joiningDate:form.joiningDate||null,version:record.version});
      setData(response.data);setForm(fields(response.data));setMessage('Employment details saved.');await onSaved?.(response.data);
    }catch(error){setError(errorText(error));}finally{setSaving(false);}
  };
  return <section className="employment-details" aria-label="Company employment details">
    <span className="eyebrow">EMPLOYMENT IN {currentCompany.name}</span><h2>Company employment details</h2>
    <p className="employment-description">Employment details belong to this company. Login and personal details stay on the shared account.</p>
    {loading&&<p role="status">Loading employment details...</p>}
    {record&&<dl className="employment-metrics"><div><dt>Employee ID</dt><dd>{record.employeeId||'Not assigned'}</dd></div>
      <div><dt>Job title</dt><dd>{record.jobTitle||'Not assigned'}</dd></div><div><dt>Joining date</dt><dd>{record.joiningDate||'Not assigned'}</dd></div></dl>}
    {canEdit&&<form className="employment-form" onSubmit={save}>
      <label>Employee ID<input inputMode="numeric" pattern="[1-9][0-9]{0,8}" maxLength={9} placeholder="Automatically assigned" value={form.employeeId} disabled={saving} onChange={event=>setForm({...form,employeeId:event.target.value})}/></label>
      <label>Job title<input maxLength={120} value={form.jobTitle} disabled={saving} onChange={event=>setForm({...form,jobTitle:event.target.value})}/></label>
      <label>Joining date<input type="date" value={form.joiningDate} disabled={saving} onChange={event=>setForm({...form,joiningDate:event.target.value})}/></label>
      <button className="button button-primary" disabled={saving}>{saving?'Saving...':'Save employment details'}</button>
    </form>}
    {!editable&&record&&<p className="employment-description">Contact your company administrator to update these details.</p>}
    {error&&<><p className="error-message" role="alert">{error}</p><button className="button button-secondary" disabled={loading||saving} onClick={()=>setAttempt(value=>value+1)}>Reload employment details</button></>}
    {message&&<p className="employment-saved" role="status">{message}</p>}
  </section>;
}
