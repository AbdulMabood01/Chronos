import React from 'react';

export default function LeaveBalanceChart({title, balance}) {
  const annual=Number(balance.allowanceDays), extra=Number(balance.extraDays);
  const used=Number(balance.usedDays), pending=Number(balance.pendingDays||0);
  const remaining=Number(balance.remainingDays), available=Math.max(0,remaining-pending);
  // Overflow is reported separately so the bar represents the entitlement.
  const total=annual+extra;
  const approvedSlice=Math.min(used,total), pendingSlice=Math.min(pending,Math.max(0,total-approvedSlice));
  const approvedPercent=total?approvedSlice/total*100:0;
  const pendingPercent=total?pendingSlice/total*100:0;
  return <figure className="leave-balance-chart">
    <figcaption>{title}</figcaption>
    <strong>{remaining} <small>days remaining</small></strong>
    <div className={`leave-balance-bar${total?'':' is-empty'}`} role="img" aria-label={`${title}: ${total} days entitlement, ${used} approved, ${pending} pending, ${available} available after pending requests`}>
      <span className="used" style={{width:`${approvedPercent}%`}}/>
      <span className="pending" style={{width:`${pendingPercent}%`}}/>
    </div>
    <dl><div><dt><i className="used"/>Approved</dt><dd>{used}</dd></div><div><dt><i className="pending"/>Pending</dt><dd>{pending}</dd></div><div><dt><i className="available"/>Available</dt><dd>{available}</dd></div></dl>
    <p>{available} days available after pending requests<br/>Annual {annual} + extra {extra} = {total} days</p>
    {!total&&<p>No allowance assigned.</p>}
    {used+pending>total&&<p role="status">{used+pending-total} days exceed the entitlement.</p>}
  </figure>;
}
