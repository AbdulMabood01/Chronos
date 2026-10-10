import {useEffect,useState} from 'react';
import {Link,useSearchParams} from 'react-router-dom';
import {useCompany} from '../CompanyContext';
import {billingAPI} from '../api';
import {money} from './Billing';
import {TERMS_VERSION} from './Legal';
import './Billing.css';
export default function PurchaseReview(){
 const {currentCompany}=useCompany(),company=currentCompany?.id;
 const [params]=useSearchParams(),id=params.get('quote');
 const [purchase,setPurchase]=useState(null),[summary,setSummary]=useState(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[accepted,setAccepted]=useState(false);
 useEffect(()=>{let active=true;setPurchase(null);setSummary(null);setError('');setAccepted(false);if(!company||!id)return;
 Promise.all([billingAPI.purchase(company,id),billingAPI.summary(company)]).then(([p,s])=>{if(active){setPurchase(p.data);setSummary(s.data);}}).catch(e=>{if(active)setError(e.response?.data?.message||'Unable to load this purchase. Return to billing and request a new quote.');});return()=>{active=false;};},[company,id]);
 async function pay(){setBusy(true);setError('');try{const r=await billingAPI.checkout(company,id,TERMS_VERSION),url=new URL(r.data.checkout_url);if(url.protocol!=='https:'||url.hostname!=='checkout.stripe.com')throw new Error();window.location.assign(url.href);}catch(e){setError(e.response?.data?.message||'Unable to open secure payment. No access change has been made.');}finally{setBusy(false);}}
 const unavailable=summary&&(!summary.checkoutEnabled||currentCompany.is_suspended),settled=purchase&&!['QUOTED','CHECKOUT'].includes(purchase.status),expired=purchase?.status==='QUOTED'&&Date.parse(purchase.expires_at)<=Date.now();
 return <main className="page-container billing-page"><header className="billing-header"><div><span className="billing-eyebrow">Company purchase</span><h1>Review and payment</h1><p>{currentCompany?.name} · One-off prepaid purchase</p></div><Link to="/billing">Back to billing</Link></header>
 {error&&<p role="alert">{error}</p>}{!id&&<p role="alert">Choose a plan in Billing first.</p>}{id&&!purchase&&!error&&<p role="status">Loading purchase…</p>}
 {purchase&&summary&&<section className="card" aria-label="Payment review"><h2>{purchase.plan_key.replaceAll('_',' ')}</h2><p>{purchase.term_months} months · {purchase.extra_seats} extra seats</p><p>Subtotal: {money(purchase.subtotal_cents)} · Discount: {money(purchase.discount_cents)}</p><p><strong>Pay once: {money(purchase.amount_cents)}</strong> before applicable tax. Stripe shows the final tax total before payment.</p><p>Service: {new Date(purchase.starts_at).toLocaleString()} → {new Date(purchase.ends_at).toLocaleString()}</p><p>No automatic renewal. Full refunds may be requested within seven days of verified payment. Confirmed refunds reverse purchased access; records remain.</p>
 {unavailable?<div role="status"><h3>Online payment is not available yet</h3><p>{currentCompany.is_suspended?'This company is suspended. Contact your administrator to restore availability.':'You can review this purchase and return to Billing. No payment has been taken. Try again when online payment is enabled.'}</p></div>:settled?<p role="status">Purchase status: {purchase.status.replaceAll('_',' ')}. Review the confirmed result in Billing.</p>:expired?<p role="alert">This quote has expired. Return to Billing for a fresh quote.</p>:<><label><input type="checkbox" checked={accepted} onChange={e=>setAccepted(e.target.checked)}/> I agree to the <Link to="/legal/terms">Terms of Use</Link> and <Link to="/legal/refunds">Refund &amp; Cancellation Policy</Link>.</label><p><Link to="/legal/privacy">Privacy Policy</Link></p><button className="button" disabled={busy||!accepted} onClick={pay}>{busy?'Opening payment…':'Continue to secure payment'}</button></>}
 <p><Link to="/billing">Return to billing</Link></p></section>}</main>;
}
