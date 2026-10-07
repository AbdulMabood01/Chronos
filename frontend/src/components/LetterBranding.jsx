import React from 'react';
import '../pages/CompanyManagement.css';
export const LETTER_LABELS={EMPLOYMENT_VERIFICATION:'Employment verification',TRAVEL:'Travel letter',VACATION:'Vacation letter'};
export async function letterError(error){
  let data=error.response?.data;
  if(data instanceof Blob){try{data=JSON.parse(await data.text());}catch{data=null;}}
  return data?.message||error.userMessage||'Unable to update or preview the letter.';
}
export function resolveLetter(configuration,type){
  const t=configuration?.templates?.find(t=>t.type===type);if(!t)return null;
  const e=configuration.employers.find(e=>e.id===t.employerId),h=configuration.signatories.find(h=>h.id===t.signatoryId);
  if(!e||!h)return null;
  return {...Object.fromEntries(['name','address','email','phone','website','identifiers','logo'].map(k=>[k,e[k]||''])),hrName:h.name||'',hrTitle:h.title||'',signature:h.signature||'',body:t.body||'',signatureRequired:t.signatureRequired};
}
export function ImageField({label,value,onChange,onError,disabled}){
  async function upload(event){const file=event.target.files?.[0];if(!file)return;if(!['image/png','image/jpeg'].includes(file.type)||file.size>1048576){onError('Choose a PNG or JPEG image up to 1 MB.');return;}try{const data=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=reject;reader.readAsDataURL(file);});onChange(data);}catch{onError('Unable to read the image.');}event.target.value='';}
  return <div className="management-field"><span>{label}</span><input aria-label={label} type="file" accept="image/png,image/jpeg" disabled={disabled} onChange={upload}/>{value&&<><img src={value} alt={`${label} preview`} style={{maxWidth:160,maxHeight:70,objectFit:'contain'}}/><button className="button button-small button-secondary" type="button" disabled={disabled} onClick={()=>onChange('')}>Remove {label.toLowerCase()}</button></>}</div>;
}
export function EmployerFields({value,onChange,onError,disabled=false}){
  return <><div className="management-form-grid">{[['name','Legal employer name',200],['address','Employer address',1000],['email','HR contact email',255],['phone','Contact phone',80],['website','Company website',255],['identifiers','Employer identifiers',1000]].map(([key,label,max])=><label className="management-field" key={key}>{label}<input type={key==='email'?'email':'text'} maxLength={max} value={value[key]||''} disabled={disabled} onChange={e=>onChange({...value,[key]:e.target.value})}/></label>)}</div><ImageField label="Company logo" value={value.logo} disabled={disabled} onError={onError} onChange={logo=>onChange({...value,logo})}/></>;
}
export function SignatoryFields({value,onChange,onError,disabled=false}){
  return <><div className="management-form-grid">{[['name','HR signatory name',200],['title','HR signatory title',120]].map(([key,label,max])=><label className="management-field" key={key}>{label}<input maxLength={max} value={value[key]||''} disabled={disabled} onChange={e=>onChange({...value,[key]:e.target.value})}/></label>)}</div><ImageField label="HR signature" value={value.signature} onError={onError} disabled={disabled} onChange={signature=>onChange({...value,signature})}/></>;
}
