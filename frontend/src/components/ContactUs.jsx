import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link } from 'react-router-dom';
import { contactAPI } from '../api';
import './ContactUs.css';

const emptyFields = { name: '', company: '', email: '', phone: '', message: '', website: '' };

export function ContactUsButton({ source = 'LANDING', className = '', children = 'Contact us' }) {
  const [open, setOpen] = useState(false);
  const [fields, setFields] = useState(emptyFields);
  const [availability, setAvailability] = useState('loading');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');
  const dialog = useRef(null);
  const trigger = useRef(null);
  const feedback = useRef(null);
  const id = useId();

  useEffect(() => {
    if (!open) return;
    dialog.current.showModal();
    dialog.current.querySelector('[name="name"]')?.focus({ preventScroll: true });
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const controller = new AbortController();
    setAvailability('loading');
    contactAPI.status(controller.signal).then(({ data }) => {
      if (!controller.signal.aborted) setAvailability(data.available ? 'ready' : 'unconfigured');
    }).catch(() => {
      if (!controller.signal.aborted) setAvailability('offline');
    });
    return () => {
      controller.abort();
      document.body.style.overflow = previousOverflow;
      trigger.current?.focus({ preventScroll: true });
    };
  }, [open]);

  useEffect(() => { if (open && (sent || error)) feedback.current?.focus(); }, [open, sent, error]);

  const close = () => { dialog.current?.close(); setOpen(false); };
  const update = event => setFields(current => ({ ...current, [event.target.name]: event.target.value }));
  async function submit(event) {
    event.preventDefault();
    if (sending || availability !== 'ready') return;
    const payload = Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, value.trim()]));
    if (!payload.name || !payload.company || !payload.message) {
      setError('Please enter your name, company and a short message.');
      return;
    }
    if (payload.phone && !/^[+0-9(). xX#-]*$/.test(payload.phone)) {
      setError('Please enter a valid phone number or leave it empty.');
      return;
    }
    setSending(true);
    setError('');
    try {
      await contactAPI.send({ ...payload, source });
      setSent(true);
      setFields(emptyFields);
    } catch (failure) {
      const status = failure.response?.status;
      setError(status === 429 ? 'Too many inquiries. Please wait 15 minutes before trying again.'
        : status === 400 ? 'Please check your contact details and try again.'
        : 'Your inquiry could not be sent. Your details are still here; please try again later.');
    } finally { setSending(false); }
  }

  return <><button ref={trigger} type="button" className={'contact-us-trigger ' + className} onClick={() => { if (sent) { setSent(false); setError(''); } setOpen(true); }} aria-haspopup="dialog">{children}</button>
    {open && createPortal(<dialog ref={dialog} className="contact-dialog" aria-labelledby={id + '-title'} aria-describedby={id + '-intro'} onCancel={event => { event.preventDefault(); close(); }} onKeyDown={event => {
      if (event.key !== 'Tab') return;
      const controls = [...event.currentTarget.querySelectorAll('button, input, textarea, select, a[href], [tabindex]')]
        .filter(el => el.tabIndex >= 0 && !el.matches(':disabled'));
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    }} onClick={event => {
      const bounds = event.currentTarget.getBoundingClientRect();
      if (event.target === event.currentTarget && (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom)) close();
    }}>
      <button type="button" className="contact-close" onClick={close} aria-label="Close contact form">×</button>
      <header className="contact-dialog-header"><span>CHRONOS · BY MAXWELL</span><h2 id={id + '-title'}>Let's talk about your team.</h2><p id={id + '-intro'}>Tell us what you have in mind. We can follow up to arrange a conversation.</p></header>
      <div className="contact-company"><span aria-hidden="true">↗</span><div><strong>Maxwell IT Solutions</strong><small>Chicago, Illinois, USA</small></div></div>
      {sent ? <div className="contact-success" role="status" ref={feedback} tabIndex={-1}><span aria-hidden="true">✓</span><h3>Thanks for reaching out.</h3><p>Your inquiry has been sent. We’ll contact you to discuss the next steps.</p><button type="button" className="button button-primary" onClick={close}>Done</button></div> : <form onSubmit={submit}>
        {availability !== 'ready' && <p className="contact-availability" role="status">{availability === 'loading' ? 'Checking contact availability…' : availability === 'unconfigured' ? 'Our contact form is getting ready. Inquiries cannot be sent yet; please check back soon.' : 'The contact form is temporarily unavailable. Close and reopen it to try again.'}</p>}
        <fieldset disabled={sending} className="contact-fields"><legend className="sr-only">Your contact details</legend>
          <div className="contact-field"><label htmlFor={id + '-name'}>Your name</label><input id={id + '-name'} name="name" autoComplete="name" required maxLength={100} value={fields.name} onChange={update} /></div>
          <div className="contact-field"><label htmlFor={id + '-company'}>Company</label><input id={id + '-company'} name="company" autoComplete="organization" required maxLength={150} value={fields.company} onChange={update} /></div>
          <div className="contact-field"><label htmlFor={id + '-email'}>Email address</label><input id={id + '-email'} name="email" type="email" autoComplete="email" required maxLength={254} value={fields.email} onChange={update} /></div>
          <div className="contact-field"><label htmlFor={id + '-phone'}>Phone number <span>(optional)</span></label><input id={id + '-phone'} name="phone" type="tel" autoComplete="tel" maxLength={40} value={fields.phone} onChange={update} /></div>
          <div className="contact-field contact-field-wide"><label htmlFor={id + '-message'}>How can we help?</label><textarea id={id + '-message'} name="message" required maxLength={3000} rows={3} placeholder="Tell us about your team or what you'd like to explore." value={fields.message} onChange={update} /></div>
          <div className="contact-trap" aria-hidden="true"><label htmlFor={id + '-website'}>Leave this field empty</label><input id={id + '-website'} name="website" tabIndex={-1} autoComplete="off" maxLength={200} value={fields.website} onChange={update} /></div>
        </fieldset>
        {error && <p className="contact-error" role="alert" ref={feedback} tabIndex={-1}>{error}</p>}
        <div className="contact-form-footer"><p>We’ll use these details to respond to your inquiry. <Link to="/legal/privacy" onClick={close}>Privacy Policy</Link></p><button type="submit" className="button button-primary" disabled={sending || availability !== 'ready'}>{sending ? 'Sending…' : 'Send inquiry'}<span aria-hidden="true">↗</span></button></div>
      </form>}
    </dialog>, document.body)}
  </>;
}
