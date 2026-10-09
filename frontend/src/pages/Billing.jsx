import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { billingAPI, companyAPI } from '../api';
import { useCompany } from '../CompanyContext';
import './Billing.css';

export const money = cents => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(cents || 0) / 100);
const date = value => value ? new Date(value).toLocaleString() : 'No fixed end';
const message = error => error.response?.data?.message || 'The billing request could not be completed. Please retry.';

export function PlanCards({ catalog, months, selected, onSelect }) {
  const discount = catalog.discounts[String(months)] || 0;
  return <div className="billing-plans">{catalog.plans.map(plan => <article key={plan.key} className={`card billing-plan${selected === plan.key ? ' selected' : ''}`}>
    <div className="billing-plan-heading"><h2>{plan.name}</h2>{selected === plan.key && <span className="billing-badge">Selected</span>}</div><p className="billing-price">{money(plan.monthlyCents)}<small> / company / month equivalent</small></p>
    <p className="billing-plan-capacity"><strong>{plan.projects}</strong> open {plan.projects === 1 ? 'project' : 'projects'} · <strong>{plan.users}</strong> {plan.key === 'FREE' ? 'people including admins' : 'company employees'}</p>
    <p className="billing-plan-total">{plan.key === 'FREE' ? 'Free without an expiry date.' : <><strong>{money(plan.monthlyCents * months * (100 - discount) / 100)}</strong> prepaid for {months} months{discount > 0 ? ` (${discount}% discount)` : ''}.</>}</p>
    <ul><li>Time tracking, expenses and independent approvals</li><li>PTO, reminders, standard reports and exports</li><li>Company and project roles; audit history</li><li>{plan.key === 'FREE' ? 'Upgrade to add capacity' : `${money(400 * (100 - discount) / 100)} per extra employee / month equivalent`}</li></ul>
    {plan.key !== 'FREE' && <p className="billing-plan-note">Admin-only accounts are free. One employee counts once across projects.</p>}
    {onSelect && plan.key !== 'FREE' && <button type="button" className="button" aria-pressed={selected === plan.key} onClick={() => onSelect(plan.key)}>Choose {plan.name}</button>}
  </article>)}</div>;
}

export function Pricing() {
  const [catalog, setCatalog] = useState(null), [months, setMonths] = useState(12), [error, setError] = useState('');
  useEffect(() => { let active = true; billingAPI.catalog().then(r => { if (active) setCatalog(r.data); }).catch(e => { if (active) setError(message(e)); }); return () => { active = false; }; }, []);
  return <main className="page-container billing-page billing-pricing"><header className="billing-header"><div><span className="billing-eyebrow">Plans &amp; pricing</span><h1>Company plans</h1><p>One plan for your company. Grow your projects and team with predictable, prepaid pricing.</p></div><Link className="billing-header-link" to="/billing">Manage billing →</Link></header>
    <div className="billing-plan-toolbar"><TermSelector months={months} setMonths={setMonths} /><p>One-off payments. No automatic renewal. Prices are in USD before applicable taxes.</p></div>
    {error && <p role="alert">{error}</p>}{catalog ? <PlanCards catalog={catalog} months={months} /> : !error && <p role="status">Loading plans…</p>}
    <section className="card"><h2>A complete workflow in every plan</h2><p>Approvals always require a different authorized reviewer. Standard exports and audit history stay available. Completed or archived projects release a slot and retain their records.</p><p>Try Pro Plus for 30 days without a card from your company Billing page.</p><p><Link to="/billing">Manage company billing</Link> · <Link to="/login">Sign in</Link></p><p>For custom capacity or rollout requirements, contact your Chronos administrator.</p></section>
  </main>;
}
function TermSelector({ months, setMonths, disabled = false }) {
  return <label className="company-field billing-term-selector">Prepaid term<select value={months} disabled={disabled} onChange={e => setMonths(Number(e.target.value))}><option value={3}>3 months</option><option value={6}>6 months — save 10%</option><option value={12}>12 months — save 20%</option></select></label>;
}

export default function Billing() {
  const { currentCompany, refreshCompanies, selectCompany } = useCompany();
  const company = currentCompany?.id;
  const [params] = useSearchParams();
  const [catalog, setCatalog] = useState(null), [summary, setSummary] = useState(null), [people, setPeople] = useState([]);
  const [months, setMonths] = useState(12), [plan, setPlan] = useState('PRO'), [extra, setExtra] = useState(0), [seatQuantity, setSeatQuantity] = useState(1);
  const [profile, setProfile] = useState({ legalName: '', email: '', address: '' });
  const [quote, setQuote] = useState(null), [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false), [reload, setReload] = useState(0), [refundReason, setRefundReason] = useState('');
  const target = params.get('company'), returnedPurchase = params.get('purchase');
  useEffect(() => { if (target && String(company) !== target) selectCompany(target).catch(e => setError(message(e))); }, [target, company, selectCompany]);
  useEffect(() => {
    let active = true; setSummary(null); setQuote(null); setError(''); setPeople([]);
    if (!company) return () => { active = false; };
    Promise.all([billingAPI.catalog(), billingAPI.summary(company), currentCompany.is_suspended ? Promise.resolve({data:[]}) : companyAPI.members(company)]).then(([c, s, p]) => {
      if (!active) return; setCatalog(c.data); setSummary(s.data); setPeople(p.data);
      setProfile({ legalName: s.data.profile.legal_name || currentCompany.name, email: s.data.profile.billing_email || '', address: s.data.profile.billing_address || '' });
    }).catch(e => { if (active) setError(message(e)); });
    return () => { active = false; };
  }, [company, reload]);
  useEffect(() => {
    if (!company || !returnedPurchase) return;
    let active = true, count = 0;
    const poll = () => billingAPI.purchase(company, returnedPurchase).then(r => {
      if (!active) return;
      if (r.data.status === 'FULFILLED') { setNotice('Payment confirmed. Your company plan and receipt are ready.'); setReload(v => v + 1); refreshCompanies(); clearInterval(timer); }
      else if (['RECONCILIATION', 'DISPUTED'].includes(r.data.status)) { setNotice('Your payment is recorded and needs billing support review. Please do not pay again.'); clearInterval(timer); }
      else if (r.data.status === 'CANCELED') { setNotice('Checkout ended without an entitlement change. You can request a new quote.'); clearInterval(timer); }
      else setNotice(params.get('canceled') ? 'You returned from Checkout. Existing access is unchanged until payment is confirmed.' : 'Checking payment status. Your browser return does not confirm payment.');
      if (++count >= 40) { clearInterval(timer); setNotice('Payment is still processing. You can safely return to Billing later.'); }
    }).catch(e => { if (active) { setError(message(e)); clearInterval(timer); } });
    const timer = setInterval(poll, 3000); poll(); return () => { active = false; clearInterval(timer); };
  }, [company, returnedPurchase]);
  async function act(callback, success) {
    setBusy(true); setError(''); setNotice(''); try { await callback(); if (success) { setNotice(success); setReload(v => v + 1); await refreshCompanies(); } }
    catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  async function download(id) { await act(async () => { const r = await billingAPI.receipt(company, id), url = URL.createObjectURL(r.data), a = document.createElement('a'); a.href = url; a.download = `chronos-receipt-${id}.pdf`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); }); }
  async function continueCheckout(id){await act(async()=>{const r=await billingAPI.checkout(company,id),url=new URL(r.data.checkout_url);if(url.protocol!=='https:'||url.hostname!=='checkout.stripe.com')throw new Error('Unexpected checkout address');window.location.assign(url.href);});}
  if (!company) return <div className="page-container"><h1>Company billing</h1><p>Select your company first.</p></div>;
  const entitlement = summary?.entitlement;
  const capacity = entitlement ? entitlement.includedUsers + entitlement.extraSeats : 0;
  const activeTerm = summary?.terms.find(t => t.id === entitlement?.termId);
  const cheaper = catalog?.plans.find(p => p.monthlyCents > 0 && p.users >= (entitlement?.activeUsers || 0) + (entitlement?.reservations || 0) && p.projects >= (entitlement?.openProjects || 0) && p.monthlyCents < (catalog.plans.find(p => p.key === entitlement?.plan)?.monthlyCents || 0) + 400 * (entitlement?.extraSeats || 0));
  return <div className="page-container billing-page"><header className="billing-header"><div><span className="billing-eyebrow">Company workspace</span><h1>Company billing</h1><p>{currentCompany.name} · One plan for the company, with employees pooled across projects.</p></div><Link className="billing-header-link" to="/pricing">View all plans →</Link></header>
    {error && <p className="inline-alert" role="alert">{error} <button onClick={() => setReload(v => v + 1)}>Reload billing</button></p>}{notice && <p role="status">{notice}</p>}
    {!summary && !error && <p role="status">Loading company billing…</p>}
    {summary && catalog && <>
      {currentCompany.is_suspended && <p role="alert">This company is administratively suspended. Billing history, receipts and refund requests remain available. Contact your administrator to restore operations; payment cannot lift suspension.</p>}
      <section className="card billing-summary"><h2>Current plan: {catalog.plans.find(p => p.key === entitlement.plan)?.name || entitlement.plan}</h2>
        <p>{entitlement.source === 'LEGACY' ? 'Legacy allowance preserved. No paid purchase is recorded.' : entitlement.source === 'COMPLIMENTARY' ? 'Complimentary company plan. No payment or card is required.' : entitlement.source === 'CONTRACT' ? 'Dated support / contract grant. No payment is recorded.' : entitlement.source === 'TRIAL' ? 'Pro Plus trial' : entitlement.source === 'PAID' ? 'Prepaid company plan' : 'Free company plan'}</p>
        {entitlement.source === 'COMPLIMENTARY' && <p><strong>{entitlement.endsAt ? 'Expires ' + date(entitlement.endsAt) : 'No expiry'}</strong> · Ask a Platform Admin to change your plan or capacity.</p>}
        {entitlement.endsAt && <p>{entitlement.source === 'FREE' ? 'Previous term ended' : 'Service ends'}: <strong>{date(entitlement.endsAt)}</strong>. No automatic renewal.</p>}
        {entitlement.grace && <p role="status">Seven-day expiry grace: existing work can continue while you renew or reduce usage. New capacity still follows Free limits.</p>}
        {entitlement.restricted && <p role="alert">Company usage exceeds its current allowance. Renew or reduce usage before submitting new work. Authorized history, exports, offboarding and pending reviews remain available.</p>}
        <dl className="billing-usage"><div><dt>Open projects</dt><dd>{entitlement.openProjects} / {entitlement.projects}</dd></div><div><dt>Active employees{entitlement.plan === 'FREE' || entitlement.source === 'LEGACY' ? ' / people' : ''}</dt><dd>{entitlement.activeUsers}</dd></div><div><dt>Reserved invitations</dt><dd>{entitlement.reservations}</dd></div><div><dt>Included capacity</dt><dd>{entitlement.includedUsers}</dd></div><div><dt>Purchased extra seats</dt><dd>{entitlement.extraSeats}</dd></div><div><dt>Seats pending refund</dt><dd>{entitlement.refundPendingSeats||0}</dd></div><div><dt>Available capacity</dt><dd>{Math.max(0, capacity - entitlement.activeUsers - entitlement.reservations - (entitlement.refundPendingSeats||0))}</dd></div></dl>
        <p>Vacant paid seats can be reassigned. Invitations never trigger a charge. Archived projects retain their records.</p>
        {!summary.profile.trial_used && entitlement.source !== 'PAID' && entitlement.source !== 'CONTRACT' && entitlement.source !== 'COMPLIMENTARY' && <button disabled={busy} className="button" onClick={() => act(() => billingAPI.trial(company), 'Your 30-day Pro Plus trial has started.')}>Start 30-day Pro Plus trial</button>}
        {entitlement.source !== 'COMPLIMENTARY' && cheaper && cheaper.key !== entitlement.plan && <p>A {cheaper.name} plan may cost less than your current plan plus seats. Review a quote before changing; upgrades never happen automatically.</p>}
      </section>
      {entitlement.source !== 'COMPLIMENTARY' && <><section className="card"><h2>Billing details</h2><form onSubmit={e => { e.preventDefault(); act(() => billingAPI.profile(company, { ...profile, revision: summary.profile.revision }), 'Billing details saved. Existing receipts remain unchanged.'); }}><fieldset className="billing-profile-fields" disabled={busy}>
        <label className="company-field">Company legal name<input required maxLength={200} value={profile.legalName} onChange={e => setProfile({ ...profile, legalName: e.target.value })} /></label>
        <label className="company-field">Billing email<input required type="email" maxLength={255} value={profile.email} onChange={e => setProfile({ ...profile, email: e.target.value })} /></label>
        <label className="company-field">Billing address<textarea required maxLength={1000} value={profile.address} onChange={e => setProfile({ ...profile, address: e.target.value })} /></label><button className="button">Save billing details</button>
      </fieldset></form></section>
      <section className="billing-plan-section"><div className="billing-section-heading"><div><span className="billing-eyebrow">Plan selection</span><h2>Choose a plan or renew</h2><p>Choose the capacity and prepaid term that fit your company.</p></div><TermSelector months={months} setMonths={v => { setMonths(v); setQuote(null); }} /></div><PlanCards catalog={catalog} months={months} selected={plan} onSelect={v => { setPlan(v); setQuote(null); }} />
        <form className="card" onSubmit={e => { e.preventDefault(); act(async () => setQuote((await billingAPI.quote(company, { plan, months, extraSeats: Number(extra), kind: 'PLAN' })).data)); }}><fieldset disabled={busy}><label className="company-field">Extra employee seats beyond the selected plan<input type="number" required min={0} max={100000} value={extra} onChange={e => { setExtra(e.target.value); setQuote(null); }} /></label><p>Same-tier early renewal starts at the current term's end. Higher-tier purchases carry remaining duration forward. Review exact dates below.</p><button className="button">Review prepaid quote</button></fieldset></form>
      </section></>}
      {entitlement.source === 'PAID' && <section className="card"><h2>Add employee seats to this term</h2><form onSubmit={e => { e.preventDefault(); act(async () => setQuote((await billingAPI.quote(company, { plan: entitlement.plan, months: activeTerm.term_months || summary.purchases.find(p => p.plan_key === entitlement.plan && p.kind === 'PLAN')?.term_months, extraSeats: Number(seatQuantity), kind: 'SEATS' })).data)); }}><fieldset disabled={busy}><label className="company-field">Additional seats<input required type="number" min={1} max={100000} value={seatQuantity} onChange={e => { setSeatQuantity(e.target.value); setQuote(null); }} /></label><p>Prorated for remaining term duration. Service still ends {date(entitlement.endsAt)}.</p><button className="button">Review prorated seat quote</button></fieldset></form></section>}
      {quote && <section className="card billing-quote" aria-label="Purchase quote"><h2>Review your purchase</h2><p>{quote.plan_key.replaceAll('_', ' ')} · {quote.kind === 'SEATS' ? `${quote.extra_seats} additional seats` : `${quote.term_months} months and ${quote.extra_seats} extra seats`}</p><p>Subtotal: {money(quote.subtotal_cents)} · Discount: {money(quote.discount_cents)}</p><p><strong>Pay once: {money(quote.amount_cents)}</strong> before applicable tax. Stripe shows the final tax total before payment.</p><p>Service: {date(quote.starts_at)} → {date(quote.ends_at)}</p><p>Quote expires {date(quote.expires_at)}. No automatic renewal or charge.</p>
        {!summary.checkoutEnabled && <p role="status">Online payment is currently unavailable. You can review prices and contact your administrator.</p>}
        <button disabled={busy || !summary.checkoutEnabled} className="button" onClick={() => continueCheckout(quote.id)}>Continue to secure payment</button><button disabled={busy} onClick={() => setQuote(null)}>Dismiss quote</button></section>}
      <section className="card"><h2>Employee access</h2><p>Enable personal time, expenses, PTO and letters for employees. On paid plans, accounts used only for administration are free. Remove employee project roles before turning off employee access.</p>{people.filter(p => p.status === 'ACTIVE').map(p => <div key={p.user_id} className="billing-person"><span>{p.first_name} {p.last_name} · {p.workforce_enabled ? 'Employee access enabled' : 'Administration only'}</span><button disabled={busy} onClick={() => act(() => billingAPI.employeeAccess(company, p.user_id, { enabled: !p.workforce_enabled, version: p.membership_version }), 'Employee access updated.')}>{p.workforce_enabled ? 'Disable employee access' : 'Enable employee access'}</button></div>)}</section>
      {entitlement.source === 'PAID' && <section className="card"><h2>Next renewal preference</h2><p>Reduce the next plan or seat allowance without changing this paid term. This preference never charges or automatically renews. You must purchase the next term manually.</p><p>Saved preference: {summary.profile.next_plan || 'None'} · {summary.profile.next_extra_seats ?? 0} extra seats.</p><button disabled={busy} onClick={() => act(() => billingAPI.renewalPreference(company, { plan, extraSeats: Number(extra), revision: summary.profile.revision }), 'Renewal preference saved. Current paid capacity is unchanged.')}>Save selected plan and seats for next renewal</button><button disabled={busy} onClick={() => act(() => billingAPI.renewalPreference(company, { plan: 'FREE', extraSeats: 0, revision: summary.profile.revision }), 'Free selected after this term. No projects or people will be deleted.')}>Choose Free after this term</button></section>}
      <section className="card"><h2>Purchases and receipts</h2><p>Full refunds can be requested within seven days of the original verified payment. Confirmed refunds reverse the purchased capacity; records are retained. Purchases with dependent changes require support review.</p><label className="company-field">Refund reason<textarea maxLength={500} value={refundReason} onChange={e => setRefundReason(e.target.value)} /></label>
        {summary.purchases.filter(p => p.status !== 'QUOTED').map(p => <article key={p.id} className="billing-purchase"><h3>{p.plan_key.replaceAll('_', ' ')} · {p.kind}</h3><p>{money(p.amount_cents + p.tax_cents)} · {p.status.replaceAll('_', ' ')} · {date(p.paid_at || p.created_at)}</p>{p.receipt_id && <><button disabled={busy} onClick={() => download(p.receipt_id)}>Download PDF receipt</button><button disabled={busy} onClick={() => act(() => billingAPI.reissue(company, p.receipt_id), 'Original receipt reissued. Payment date is unchanged.')}>Reissue receipt</button></>}
          {p.status==='CHECKOUT'&&<><button disabled={busy||!summary.checkoutEnabled} onClick={()=>continueCheckout(p.id)}>Resume secure checkout</button><button disabled={busy||!summary.checkoutEnabled} onClick={()=>act(()=>billingAPI.cancelCheckout(company,p.id),'Checkout cancellation checked. Confirmed payments remain recorded.')}>Cancel unpaid checkout</button></>}
          {['FULFILLED', 'RECONCILIATION', 'REFUND_FAILED'].includes(p.status) && p.paid_at && Date.now() <= new Date(p.paid_at).getTime() + 7 * 86400000 && <button disabled={busy || !refundReason.trim()} onClick={() => act(() => billingAPI.refund(company, p.id, refundReason), 'Refund requested. Capacity changes only after provider confirmation.')}>Request full refund and reverse capacity</button>}
          {p.status === 'RECONCILIATION' && <p role="alert">Payment recorded; contact billing support. Do not purchase again to resolve this.</p>}
        </article>)}{!summary.purchases.some(p => p.status !== 'QUOTED') && <p>No purchases yet.</p>}</section>
      <section className="card"><h2>Billing activity</h2>{!summary.activity.length && <p className="billing-empty">No billing activity yet.</p>}{summary.activity.map((a, i) => <p key={i}>{date(a.created_at)} · {a.action.replaceAll('_', ' ')}{a.reason ? ` · ${a.reason}` : ''}</p>)}</section>
    </>}
  </div>;
}
