import {useEffect,useRef,useState} from 'react';
import {useCompany} from '../CompanyContext';
import {settingsAPI} from '../api';
import ScreenTitle from '../components/ScreenTitle';
import {LoadingIndicator} from '../components/Hourglass';
import '../styles.css';
import './LeavePolicy.css';

const companyFields=[
  {key:'company_name',title:'Company display name',type:'text',description:'Used on company timesheet PDFs. Your workspace ID stays the same.'},
  {key:'vacation_days_per_year',title:'Paid vacation days',type:'number',description:'Default annual vacation allowance.'},
  {key:'sick_days_per_year',title:'Sick days',type:'number',description:'Default annual sick leave allowance.'},
  {key:'bereavement_days_per_year',title:'Bereavement days',type:'number',description:'Default annual bereavement allowance.'},
  {key:'timesheet.reminders.enabled',title:'Timesheet reminders',type:'boolean',description:'Remind eligible project members when their reporting period closes: from 5:00 PM local time, with a late reminder after midnight. Delivery also requires the platform reminder service to be enabled.'},
];
const platformFields=[{key:'platform.timesheet.reminders.enabled',title:'Reminder delivery',type:'boolean',description:'Enable or pause timesheet reminders across the platform. Each company also controls its own reminders.'}];
const messageFor=error=>error?.userMessage||error?.response?.data?.message||'Unable to load settings. Try again.';

export default function Settings({platform=false}) {
  const context=useCompany();
  const {currentCompany,platformAdmin,companyCapabilities,platformCapabilities,loading:contextLoading,switching}=context;
  const allowed=!contextLoading&&!switching&&(platform
    ?platformAdmin&&platformCapabilities?.canConfigurePlatform===true
    :!platformAdmin&&currentCompany&&companyCapabilities?.canManageCompanySettings===true);
  const resource=allowed?(platform?'platform':`company:${currentCompany.id}`):null;
  const scope=useRef(resource);scope.current=resource;
  const generation=useRef(0);
  const [snapshot,setSnapshot]=useState(null),[loading,setLoading]=useState(true),[error,setError]=useState('');
  const [editing,setEditing]=useState(null),[draft,setDraft]=useState(''),[saving,setSaving]=useState(false),[saved,setSaved]=useState(false);
  const [stale,setStale]=useState(false),[attempt,setAttempt]=useState(0);
  const fields=platform?platformFields:companyFields;
  useEffect(()=>{
    const ticket=++generation.current;setSnapshot(null);setLoading(true);setError('');setEditing(null);setDraft('');setSaved(false);setStale(false);setSaving(false);
    if(!resource){setLoading(false);return;}
    const request=platform?settingsAPI.platform():settingsAPI.company(currentCompany.id);
    request.then(({data})=>{
      if(ticket!==generation.current||scope.current!==resource)return;
      if(!platform&&String(data.companyId)!==String(currentCompany.id))throw new Error('Settings response did not match this company.');
      setSnapshot({...data,resource});
    }).catch(err=>{if(ticket===generation.current&&scope.current===resource)setError(messageFor(err));})
      .finally(()=>{if(ticket===generation.current&&scope.current===resource)setLoading(false);});
    return()=>{generation.current++;};
  },[resource,attempt,platform,currentCompany?.id]);
  const record=snapshot?.resource===resource?snapshot:null;
  const update=async(key,value)=>{
    if(!record||saving||stale)return;
    const ticket=generation.current,target=resource;setSaving(true);setSaved(false);setError('');
    try{
      const response=platform?await settingsAPI.updatePlatform(key,value,record.version):await settingsAPI.updateCompany(currentCompany.id,key,value,record.version);
      if(ticket!==generation.current||scope.current!==target)return;
      if(!platform&&String(response.data.companyId)!==String(currentCompany.id))throw new Error('Settings response did not match this company.');
      setSnapshot({...response.data,resource:target});setEditing(null);setSaved(true);
    }catch(err){if(ticket===generation.current&&scope.current===target){setError(messageFor(err));if(err.response?.status===409)setStale(true);}}
    finally{if(ticket===generation.current&&scope.current===target)setSaving(false);}
  };
  if(!allowed)return <div className="page-container"><p role="alert">You do not have permission to access these settings.</p></div>;
  return <div className="page-container settings-page">
    <ScreenTitle title={platform?'Platform settings':'Company settings'} icon="settings" eyebrow={platform?'PLATFORM CONFIGURATION':currentCompany.name}/>
    <p className="page-subtitle">{platform?'Manage shared reminder delivery.':'Manage defaults for this company.'}</p>
    {error&&<div className="error-message" role="alert">{error}</div>}
    {saved&&<p className="success-message" role="status">Setting updated.</p>}
    {loading?<LoadingIndicator label="Loading settings..."/>:<>
      <button className="button button-secondary" disabled={saving} onClick={()=>setAttempt(value=>value+1)}>Reload settings</button>
      {stale&&<p>Another administrator changed these settings. Reload settings before saving again.</p>}
      {record&&<>
        {!platform&&<p>Save leave defaults here, then preview and apply the annual policy in Leave management. Personal overrides and extra grants are preserved.</p>}
        <div className="settings-card-grid">{fields.map(field=>{
          const value=record.values[field.key];
          return <article className="settings-control-card" key={field.key}>
            <div><h2>{field.title}</h2><p>{field.description}</p></div>
            {field.type==='boolean'?<button className={`settings-toggle ${value==='true'?'active':''}`} type="button" aria-label={field.title} aria-pressed={value==='true'} disabled={saving||stale} onClick={()=>update(field.key,value==='true'?'false':'true')}>{value==='true'?'On':'Off'}</button>
              :editing===field.key?<form className="setting-card-edit" onSubmit={event=>{event.preventDefault();update(field.key,draft);}}>
                <label>{field.title}<input className="setting-card-input" type={field.type} min={field.type==='number'?0:undefined} max={field.type==='number'?366:undefined} step={field.type==='number'?'0.01':undefined} maxLength={150} required value={draft} autoFocus disabled={saving} onChange={event=>setDraft(event.target.value)}/></label>
                <button className="button button-small button-primary" disabled={saving||stale}>{saving?'Saving...':'Save'}</button>
                <button className="button button-small button-secondary" type="button" disabled={saving} onClick={()=>setEditing(null)}>Cancel</button>
              </form>:<div className="setting-card-value"><strong>{value}{field.type==='number'?' days':''}</strong><button className="button button-small button-secondary" type="button" disabled={saving||stale} onClick={()=>{setEditing(field.key);setDraft(value);setSaved(false);}}>Change {field.title}</button></div>}
          </article>;
        })}</div>
      </>}
    </>}
  </div>;
}
