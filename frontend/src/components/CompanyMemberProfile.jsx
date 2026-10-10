import {useEffect,useState} from 'react';
import {useCompany} from '../CompanyContext';
import {companyAPI} from '../api';
import ProfileForm from './ProfileForm';
import DetailCorrectionRequests from './DetailCorrectionRequests';
export default function CompanyMemberProfile({userId}) {
 const {currentCompany}=useCompany();const companyId=currentCompany?.id;
 const [record,setRecord]=useState(null),[error,setError]=useState(''),[revision,setRevision]=useState(0);
 const profile=record&&String(record.company)===String(companyId)&&String(record.target)===String(userId)?record.data:null;
 useEffect(()=>{let active=true;setRecord(null);setError('');
  companyAPI.memberProfile(companyId,userId).then(r=>{if(active)setRecord({company:companyId,target:userId,data:r.data});}).catch(e=>{if(active)setError(e.userMessage||e.response?.data?.message||'Unable to load profile.');});
  return()=>{active=false;};
 },[companyId,userId,revision]);
 return <section className="company-member-personal-profile" aria-label="Member personal profile">
  {error&&<p role="alert">{error} <button type="button" className="button button-secondary" onClick={()=>setRevision(v=>v+1)}>Reload profile</button></p>}
  {!profile&&!error&&<p role="status">Loading profile...</p>}
  {profile&&<ProfileForm key={userId+'-'+revision} user={profile} readOnly showBloodGroup={false}/>}
  <DetailCorrectionRequests userId={userId} allowRequest={false}/>
 </section>;
}
