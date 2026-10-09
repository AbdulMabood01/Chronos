import React,{useEffect,useState} from 'react';
import {useCompany} from '../CompanyContext';
import {companyLetterRequestAPI} from '../api';
import {LoadingIndicator} from '../components/Hourglass';
import {EmployerFields,SignatoryFields,letterError} from '../components/LetterBranding';
import './CompanyManagement.css';
const emptyEmployer={id:'company',name:'',address:'',email:'',phone:'',website:'',identifiers:'',logo:''};
const emptyHR={id:'hr',name:'',title:'',signature:''};
export default function LetterSettings(){
  const {currentCompany}=useCompany(),id=currentCompany.id;
  const [configuration,setConfiguration]=useState(null),[error,setError]=useState(''),[notice,setNotice]=useState(''),[busy,setBusy]=useState(false),[revision,setRevision]=useState(0);
  useEffect(()=>{let active=true;setConfiguration(null);setError('');setNotice('');companyLetterRequestAPI(id).configuration().then(r=>{if(active)setConfiguration({...r.data,employers:[r.data.employers[0]||{...emptyEmployer,name:currentCompany.name}],signatories:[r.data.signatories[0]||emptyHR]});}).catch(async e=>{const message=await letterError(e);if(active)setError(message);});return()=>{active=false;};},[id,revision]);
  function change(key,value){setConfiguration(c=>({...c,[key]:[value]}));setNotice('');}
  async function save(event){event.preventDefault();setBusy(true);setError('');try{const r=await companyLetterRequestAPI(id).saveConfiguration(configuration);setConfiguration(r.data);setNotice('Company and HR details saved for all letters.');}catch(e){setError(await letterError(e));}finally{setBusy(false);}}
  return <section className="management-section letter-shared-settings" aria-label="Company and HR details">
    <div className="management-toolbar"><div><h2>Company &amp; HR details</h2><p>Enter these once. All three letter types use the same company details and HR signature.</p></div><button type="button" className="button button-secondary" disabled={busy} onClick={()=>setRevision(r=>r+1)}>Reload letter settings</button></div>
    {error&&<p className="error-message" role="alert">{error}</p>}{notice&&<p className="success-message" role="status">{notice}</p>}
    {!configuration?(!error&&<LoadingIndicator label="Loading letter settings..."/>):<form onSubmit={save}>
      <fieldset disabled={busy} className="letter-settings-fields">
        <section className="card management-section"><h3>Company details</h3><EmployerFields value={configuration.employers[0]} onChange={value=>change('employers',value)} onError={setError}/></section>
        <section className="card management-section"><h3>HR details &amp; signature</h3><SignatoryFields value={configuration.signatories[0]} onChange={value=>change('signatories',value)} onError={setError}/></section>
      </fieldset>
      <div className="management-actions"><button className="button button-primary" disabled={busy}>{busy?'Saving...':'Save letter settings'}</button><span>Applies to future letters. Previously issued letters remain unchanged.</span></div>
    </form>}
  </section>;
}
