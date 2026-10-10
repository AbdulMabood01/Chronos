import { ContactUsButton } from '../components/ContactUs';
import {Link,useParams} from 'react-router-dom';
import './Legal.css';
export const TERMS_VERSION='2026-10-09';
const supportEmail=import.meta.env.VITE_SUPPORT_EMAIL||'';
export const policies={
 privacy:{title:'Privacy Policy',intro:'Maxwell IT Solutions, Chicago, Illinois, USA operates Chronos. This policy explains how account, workspace and billing information is used.',sections:[
 ['Information we handle','Account and work identity, company membership and role assignments, profile details, time and expense records, leave requests, employment letters, communications, feedback, reviews, uploaded files and workplace reports may be processed when you or your company use those features. Authentication, access, delivery and billing events are recorded to operate and protect the service.'],
 ['Why we process information','We use information to provide company workflows, enforce permissions, deliver requested messages, process payments and refunds, support customers and investigate service or security problems. Companies determine which employee information they enter and who may access it.'],
 ['Who can access information','Company and project permissions govern workspace access. Platform administration provides provisioning and billing metadata rather than unrestricted access to private employee records. Authorized infrastructure, email and payment providers process information needed for their services. Information may also be disclosed when legally required.'],
 ['Payments','Stripe hosts payment collection. Chronos stores payment references, amounts, status, billing details and receipts; hosted payment entry is handled by Stripe. Review Stripe’s privacy information when using checkout.'],
 ['Retention and your requests','Expiry does not automatically delete company records. Company data remains until an authorized company deletion request, subject to applicable recordkeeping duties and legal holds. Refer to Data Retention & Deletion for the request process. Employees should contact their Company Admin about workspace access, corrections and employer-owned records.'],
 ['Security and policy changes','We use scoped authorization, authentication and audit controls. Report suspected unauthorized access promptly. Changes appear on this page with a new version date; material changes to data use will be communicated before they take effect.']]},
 terms:{title:'Terms of Use',intro:'These terms govern use of Chronos provided by Maxwell IT Solutions, Chicago, Illinois, USA. An administrator purchasing for a company must have authority to act for it.',sections:[
 ['Accounts and responsibilities','Provide accurate account and billing information, protect credentials and use only access authorized by your company. Your company is responsible for its users, assignments, submitted records and lawful instructions concerning employee data. Never share another person’s login or approve your own work.'],
 ['Free access and trials','Free includes one open project and seven active people including administrators, for sixty days. Existing Free companies receive sixty days from rollout; new companies receive sixty days from creation. One card-free Pro Plus trial may last up to thirty days and cannot extend beyond the original Free deadline. Switching plans or administrators does not restart Free eligibility. A Platform Admin may issue an audited Free extension with an explicit end date.'],
 ['Paid purchases','Paid plans use one-off prepaid purchases for the company. Displayed monthly rates are equivalents rather than recurring charges. The purchase review identifies the term, seats, discount and price before applicable tax; hosted checkout shows the final amount. There is no automatic renewal. Verified payment is required to activate purchased access.'],
 ['Expiry and suspension','After Free expires, new operational submissions and capacity expansion require paid access or an authorized dated grant. History, authorized exports, billing and pending reviews remain available. Paid expiry may have the stated seven-day grace for existing work. Payment does not lift administrative suspension. Expiry, refund and suspension do not automatically delete records.'],
 ['Refunds and cancellation','The Refund & Cancellation Policy applies to purchases. A full refund requested within seven days of verified payment reverses purchased access after provider confirmation. Ending use of the service does not itself create a refund or delete company records.'],
 ['Content and service rights','You and your company retain ownership of your content and permit us to process it to provide the service. Maxwell IT Solutions retains rights in Chronos software and branding. Do not copy proprietary materials, bypass access controls or misuse the service.'],
 ['Availability and disputes','The service may be interrupted for maintenance or failures. No uptime SLA is included unless separately agreed in writing. To the extent permitted by law, the service is provided as available; mandatory statutory rights remain unaffected. Illinois law governs these terms except where mandatory law requires otherwise. Contact Maxwell IT Solutions to resolve a dispute before pursuing formal proceedings.'],
 ['Changes','The accepted Terms version and acceptance time are recorded for checkout. Changes are published with a version date; purchases retain their agreed prices and term snapshots.']]},
 refunds:{title:'Refund & Cancellation Policy',intro:'Chronos purchases are prepaid, one-off company purchases with no automatic renewal.',sections:[
 ['Seven-day refund window','A Company Admin may request a full refund through Billing within seven days of the original verified payment. Reissuing a receipt does not reset this deadline. Rights required by applicable law are preserved.'],
 ['When access changes','A pending refund leaves purchased access in place until provider confirmation. A confirmed base-plan refund reverses that purchase and may restore a still-valid predecessor. Confirmed extra-seat refunds reverse the purchased seats. Company history and receipts remain.'],
 ['Capacity and dependent purchases','Release occupied seats and reserved invitations before refunding extra seats when necessary. Purchases with dependent changes require coordinated support review. Partial refunds, disputes and coordinated reversals are reviewed rather than automatically resolved.'],
 ['Processing','Refunds return through the original payment provider. Provider and bank processing times vary; a submitted request is not confirmation that money has arrived. Billing displays the processing status.'],
 ['Cancellation and renewal','Cancel an unpaid checkout from Billing. If payment has already completed, use the refund process. To stop after a prepaid term, do not purchase another term. No recurring charge is scheduled. Cancellation does not request data deletion.']]},
 'acceptable-use':{title:'Acceptable Use Policy',intro:'Use Chronos for authorized, lawful company workflows.',sections:[
 ['Protect people and data','Do not upload unlawful material, harass others, impersonate people, reveal confidential information without permission, or enter personal data without a legitimate business basis. Companies must follow applicable employment and privacy obligations.'],
 ['Protect the service','Do not bypass permissions, attempt credential theft, spread malware, exploit vulnerabilities, disrupt availability, perform unauthorized automated extraction or falsify work and payment records.'],
 ['Enforcement','We may restrict access to address abuse, security incidents or legal requirements. Company and platform actions follow their authorized scope; operational suspension preserves historical records. Report suspected misuse using the support contact when available or notify your Company Admin.']]},
 retention:{title:'Data Retention & Deletion',intro:'Company access expiry does not automatically delete company data.',sections:[
 ['Retention after expiry','Workspace records remain until the company requests deletion. Authorized history, exports, billing and pending approvals remain available after Free expiry. Removing a membership revokes access and preserves history; deleting a global identity is a separate process.'],
 ['Requesting deletion','An authorized Company Admin must request company deletion and verify their authority. Export needed records first and identify the company and scope of the request. Employees should route employer-owned record requests through their company. Deletion is a support-managed process, not an automatic action on plan expiry.'],
 ['Exceptions and confirmation','A deletion review identifies records subject to mandatory accounting, dispute, security or legal retention. We will explain applicable exceptions and confirm the scope completed. No fixed deletion or backup-purge deadline is promised here until deployment retention procedures are established.'],
 ['Your company’s obligations','Your company is responsible for deciding which employment records it must retain and for responding to employee requests. This policy does not replace those obligations.']]},
 dpa:{title:'Data Processing Agreement',intro:'Business-customer processing terms for Maxwell IT Solutions and the company using Chronos. Deployment-specific schedules must be completed before this document is executed as a customer agreement.',sections:[
 ['Roles and instructions','The customer controls its workspace data and provides documented lawful instructions. Maxwell IT Solutions processes that data to deliver Chronos, including storage, authorized workflows, exports, delivery and billing support. Customer instructions are subject to applicable law.'],
 ['Processing scope','Subjects include customer employees, contractors and administrators. Data may include identity, membership, employment, work submissions, leave, communications, review and report information entered by the customer. Processing lasts through the service and the applicable retention period. Do not enter data outside the agreed deployment scope.'],
 ['Confidentiality and security','Personnel access must be authorized and subject to confidentiality. Scoped access and authentication govern records. Security measures, hosting locations, backup procedures and incident contacts are specified in the deployment schedule; no unverified certification is represented by this document.'],
 ['Providers and transfers','The deployment schedule must identify hosting, email, payment and other subprocessors, their locations and the customer notification process. Any legally required international-transfer safeguards must be agreed before the affected processing begins.'],
 ['Assistance and incidents','We will assist the customer with applicable data-subject requests, incident response and compliance inquiries concerning our processing. Security incident notification follows applicable law and agreed deployment procedures. Requests must respect other customers’ confidentiality.'],
 ['Return, deletion and review','Authorized exports support return of workspace data. Company deletion is handled on a verified request subject to lawful retention exceptions. The customer may request information about applicable controls; additional audits and costs require an agreed scope.'],
 ['Execution schedule','Complete the customer legal identity, authorized signatories, support/security contact, subprocessors, processing locations, technical measures, retention/deletion procedures and applicable transfer terms before execution. This schedule remains pending while the support email and deployment details are being finalized.']]},
 cookies:{title:'Cookies & Browser Storage',intro:'Chronos uses browser storage for essential application behavior and preferences.',sections:[
 ['Essential storage','Authentication/session state, selected company and display preferences may use cookies, local storage or session storage. These support sign-in, company selection and themes. Clearing storage may sign you out or reset preferences.'],
 ['Optional tracking','These disclosures cover the current application. Advertising or optional analytics must be assessed and disclosed before being introduced, including consent controls where required. Stripe-hosted checkout has its own browser-storage practices.']]},
 support:{title:'Support & Security Contact',intro:'Maxwell IT Solutions · Chicago, Illinois, USA',sections:[['Company assistance','For workspace permissions, employee records and approval routing, contact your Company Admin. For billing, use the company Billing page.'],['Platform contact',supportEmail?'Contact '+supportEmail+' for service, privacy, deletion or security requests.':'The public support email is being finalized. Existing customers should use their established Maxwell IT Solutions contact. Do not send passwords, reset tokens or card details.']]}
};
const footerGroups = [
  { title: 'Legal', keys: ['terms', 'refunds', 'acceptable-use', 'dpa'] },
  { title: 'Privacy & support', keys: ['privacy', 'retention', 'cookies', 'support'] },
];
export function LegalFooter({ landing = false }) {
  return <footer className={`legal-footer${landing ? ' legal-footer-landing' : ''}`}>
    <div className="legal-footer-grid">
      <div className="legal-footer-brand">
        <Link to="/" aria-label="Chronos home">chronos<span>BY MAXWELL</span></Link>
        <p>A better rhythm for your workday.</p>
      </div>
      {landing && <nav aria-label="Product links"><h2>Product</h2>
        <a href="#demo">Explore Chronos</a><a href="#film">Watch the film</a>
        <Link to="/pricing">Plans</Link><Link to="/login">Sign in</Link><ContactUsButton source="LANDING" />
      </nav>}
      {footerGroups.map(group => <nav key={group.title} aria-label={group.title}>
        <h2>{group.title}</h2>
        {group.keys.map(key => <Link key={key} to={'/legal/' + key}>{policies[key].title}</Link>)}
      </nav>)}
    </div>
    <div className="legal-footer-bottom">
      <small>© {new Date().getFullYear()} Maxwell IT Solutions. All rights reserved.</small>
      {landing ? <a href="#lp-main">Back to top <span aria-hidden="true">↑</span></a> : <Link to="/">Product overview <span aria-hidden="true">↗</span></Link>}
    </div>
  </footer>;
}
export default function Legal(){const {policy}=useParams(),p=policies[policy];return <main className="page-container legal-page"><Link to="/pricing">Chronos plans</Link>{p?<article><p className="billing-eyebrow">Maxwell IT Solutions · Version {TERMS_VERSION}</p><h1>{p.title}</h1><p>{p.intro}</p>{p.sections.map(([title,body])=><section key={title}><h2>{title}</h2><p>{body}</p></section>)}{policy!=='support'&&<p><Link to="/legal/support">Support &amp; security contact</Link></p>}</article>:<><h1>Policy not found</h1><Link to="/legal/terms">Terms of Use</Link></>}</main>;}
