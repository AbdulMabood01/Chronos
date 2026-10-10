import { ContactUsButton } from '../components/ContactUs';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { LegalFooter } from './Legal';
import './Landing.css';

const chapters = [
  { label: 'Capture', title: 'A little less admin. A lot more momentum.', text: 'Bring your daily hours and project work into one clear timesheet. Build your week, then send it for review.', screen: 'Your week, in focus.', caption: 'TIME & PROJECTS' },
  { label: 'Review', title: 'Keep the work moving.', text: 'Give reviewers a clear place to handle submitted time and expenses. Follow each item from submission to decision.', screen: 'Decisions, without the chase.', caption: 'APPROVALS & EXPENSES' },
  { label: 'Connect', title: 'One team. A shared rhythm.', text: 'Bring company announcements, time off and employee requests into the same workspace as everyday work.', screen: 'The whole team, connected.', caption: 'PEOPLE & COMPANY' },
];
const promoVideo = import.meta.env.VITE_PROMO_VIDEO_URL || '/media/chronos-promo.mp4';

function Arrow() { return <span aria-hidden="true">↗</span>; }
function Clock() {
  return <div className="lp-clock" aria-hidden="true"><div className="lp-clock-face">{Array.from({ length: 12 }, (_, i) => <i key={i} style={{ '--tick': i }} />)}<span className="lp-clock-hour" /><span className="lp-clock-minute" /><span className="lp-clock-pin" /></div></div>;
}
function WorkspacePreview({ chapter = 0 }) {
  return <div className="lp-workspace">
    <div className="lp-window-bar"><span><i /><i /><i /></span><small>CHRONOS / SAMPLE WORKSPACE</small><span className="lp-window-avatar">JD</span></div>
    <div className="lp-workspace-body"><aside aria-hidden="true"><b>c<span>·</span></b><span className={chapter === 0 ? 'selected' : ''}>◷</span><span className={chapter === 1 ? 'selected' : ''}>✓</span><span className={chapter === 2 ? 'selected' : ''}>☷</span><span>▧</span></aside>
    <div className="lp-workspace-content"><div className="lp-preview-heading"><div><small>{chapters[chapter].caption}</small><h3>{chapters[chapter].screen}</h3></div><span className="lp-preview-dot" /></div>
    {chapter === 0 ? <><div className="lp-stat-row"><div><small>This week</small><strong>32<span> h</span></strong></div><div><small>Projects</small><strong>03</strong></div><div><small>Status</small><strong className="lp-stat-word">In progress</strong></div></div><div className="lp-week-chart" aria-label="Illustrative daily hours: Monday 8, Tuesday 6, Wednesday 8, Thursday 7, Friday 3">{[8, 6, 8, 7, 3].map((v, i) => <div key={i}><span style={{ '--bar': v / 8 }}><b>{v}h</b></span><small>{['MON', 'TUE', 'WED', 'THU', 'FRI'][i]}</small></div>)}</div><div className="lp-preview-bottom"><span><i /> Website redesign</span><span>24 hours this week</span></div></> : chapter === 1 ? <><div className="lp-review-summary"><span className="lp-review-symbol">✓</span><div><strong>Ready for a fresh perspective.</strong><p>Time and expenses awaiting review</p></div></div>{[['JD', 'Jordan Davis', 'Weekly timesheet', '32 h'], ['AL', 'Alex Lee', 'Travel expense', '$84.00'], ['SK', 'Sam Kim', 'Weekly timesheet', '38 h']].map(([initials, name, type, value]) => <div className="lp-demo-row" key={initials}><span className="lp-person">{initials}</span><div><strong>{name}</strong><small>{type}</small></div><b>{value}</b><span className="lp-pill">Pending</span></div>)}</> : <><div className="lp-announcement"><small>COMPANY ANNOUNCEMENT</small><h4>Good work starts with a connected team.</h4><p>Your next team update has a home here.</p></div><div className="lp-demo-row"><span className="lp-person">AL</span><div><strong>Time off request</strong><small>Alex Lee · 2 days</small></div><span className="lp-pill">Pending</span></div><div className="lp-demo-row"><span className="lp-person">JD</span><div><strong>Employment letter</strong><small>Jordan Davis · Request submitted</small></div><span className="lp-pill">In review</span></div></>}
    </div></div><div className="lp-preview-label">Illustrative product preview · Sample data</div>
  </div>;
}

export default function Landing() {
  const pageRef = useRef(null);
  const demoRef = useRef(null);
  const [chapter, setChapter] = useState(0);
  const [videoError, setVideoError] = useState(false);
  useEffect(() => {
    const previousTitle = document.title;
    document.title = 'Chronos — A better rhythm for your workday';
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const compact = window.matchMedia('(max-width: 760px) and (max-height: 680px)');
    const page = pageRef.current;
    const revealElements = [...page.querySelectorAll('.lp-section-heading, .lp-feature-grid article, .lp-start li, .lp-faq, .lp-final h2')];
    revealElements.forEach(el => el.classList.add('lp-reveal'));
    const layers = [...demoRef.current.querySelectorAll('[data-chapter]')];
    const clamp = value => Math.min(1, Math.max(0, value));
    const smooth = (from, to, value) => { const t = clamp((value - from) / (to - from)); return t * t * (3 - 2 * t); };
    let frame = 0;
    const hero = page.querySelector('.lp-hero-scene');
    const pointer = event => {
      if (motion.matches || event.pointerType === 'touch') return;
      const bounds = hero.getBoundingClientRect();
      hero.style.setProperty('--pointer-x', ((event.clientX - bounds.left) / bounds.width - .5) * 2);
      hero.style.setProperty('--pointer-y', ((event.clientY - bounds.top) / bounds.height - .5) * 2);
    };
    const resetPointer = () => { hero.style.setProperty('--pointer-x', 0); hero.style.setProperty('--pointer-y', 0); };
    hero.addEventListener('pointermove', pointer);
    hero.addEventListener('pointerleave', resetPointer);
    const paint = progress => {
      demoRef.current.style.setProperty('--demo-scroll', progress);
      const first = smooth(0.26, 0.40, progress);
      const second = smooth(0.60, 0.74, progress);
      const weights = [1 - first, first * (1 - second), second];
      const phase = first + second;
      const burst = Math.max(Math.sin(first * Math.PI), Math.sin(second * Math.PI));
      demoRef.current.style.setProperty('--depth-burst', burst);
      demoRef.current.style.setProperty('--deck-phase', phase);
      layers.forEach(el => {
        const index = Number(el.dataset.chapter);
        el.style.setProperty('--layer-opacity', weights[index]);
        el.style.setProperty('--layer-reveal', index === 0 ? 1 : index === 1 ? first : second);
        el.style.setProperty('--layer-offset', index - phase);
        el.style.setProperty('--layer-distance', Math.abs(index - phase));
        el.style.setProperty('--card-visibility', Math.max(0, 1 - Math.abs(index - phase)));
      });
    };
    const update = () => {
      frame = 0;
      const demo = demoRef.current;
      if (!page || !demo) return;
      const reduced = motion.matches;
      const heroProgress = clamp(window.scrollY / window.innerHeight);
      page.style.setProperty('--hero-scroll', reduced ? 0 : heroProgress);
      const film = page.querySelector('.lp-film');
      const rect = film.getBoundingClientRect();
      film.style.setProperty('--film-scroll', reduced ? 1 : clamp((window.innerHeight - rect.top) / (window.innerHeight * .75)));
      if (!compact.matches && !reduced) {
        const bounds = demo.getBoundingClientRect();
        const progress = clamp(-bounds.top / Math.max(1, bounds.height - window.innerHeight));
        setChapter(Math.min(2, Math.floor(progress * chapters.length)));
        paint(progress);
      } else {
        const active = [...demo.querySelectorAll('.lp-chapter-tabs button')].findIndex(el => el.getAttribute('aria-pressed') === 'true');
        layers.forEach(el => {
          el.style.setProperty('--layer-opacity', Number(el.dataset.chapter) === Math.max(0, active) ? 1 : 0);
          el.style.setProperty('--layer-reveal', Number(el.dataset.chapter) === Math.max(0, active) ? 1 : 0);
          el.style.setProperty('--layer-offset', 0);
          el.style.setProperty('--layer-distance', 0);
        });
      }
      revealElements.forEach(el => {
        const progress = reduced ? 1 : clamp((window.innerHeight - el.getBoundingClientRect().top) / (window.innerHeight * .28));
        el.style.setProperty('--reveal', progress);
      });
    };
    const schedule = () => { if (!frame) frame = requestAnimationFrame(update); };
    window.addEventListener('scroll', schedule, { passive: true });
    window.addEventListener('resize', schedule);
    motion.addEventListener('change', schedule);
    compact.addEventListener('change', schedule);
    update();
    return () => {
      hero.removeEventListener('pointermove', pointer);
      hero.removeEventListener('pointerleave', resetPointer);
      document.title = previousTitle;
      window.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', schedule);
      motion.removeEventListener('change', schedule);
      compact.removeEventListener('change', schedule);
      cancelAnimationFrame(frame);
    };
  }, []);
  function selectChapter(index) {
    setChapter(index);
    if (window.matchMedia('(max-width: 760px) and (max-height: 680px), (prefers-reduced-motion: reduce)').matches) {
      demoRef.current.querySelectorAll('[data-chapter]').forEach(el => {
        el.style.setProperty('--layer-opacity', Number(el.dataset.chapter) === index ? 1 : 0);
        el.style.setProperty('--layer-reveal', Number(el.dataset.chapter) === index ? 1 : 0);
        el.style.setProperty('--layer-offset', 0);
      });
      return;
    }
    const demo = demoRef.current;
    const travel = demo.offsetHeight - window.innerHeight;
    window.scrollTo({ top: window.scrollY + demo.getBoundingClientRect().top + travel * ((index + 0.12) / chapters.length), behavior: 'smooth' });
  }
  return <div className="lp" ref={pageRef}>
    <a className="lp-skip" href="#lp-main">Skip to content</a>
    <header className="lp-header"><Link to="/" className="lp-brand" aria-label="Chronos home"><svg className="lp-brand-icon" aria-hidden="true" viewBox="0 0 24 30" fill="none"><path d="M4 2h16M4 28h16M6 3v5c0 4 6 4 6 7s-6 3-6 7v5M18 3v5c0 4-6 4-6 7s6 3 6 7v5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round"/><path d="m8 7 4 4 4-4M8 25l4-5 4 5" fill="#ab8152"/></svg>chronos<span className="lp-brand-by">BY MAXWELL</span></Link><nav aria-label="Main navigation"><a href="#demo">Product</a><a href="#film">The film</a><Link to="/pricing">Plans</Link><ContactUsButton source="LANDING" /></nav><Link to="/login" className="lp-signin">Sign in <Arrow /></Link></header>
    <main id="lp-main">
      <section className="lp-hero"><div className="lp-hero-copy"><p className="lp-kicker"><span /> A BETTER RHYTHM FOR YOUR WORKDAY</p><h1>Your time.<br />Your team.<br /><em>In harmony.</em></h1><p className="lp-intro">Hours, projects, approvals and people.<br />One thoughtful workspace to bring it all together.</p><div className="lp-actions"><Link className="lp-button" to="/login">Get started <Arrow /></Link><a className="lp-text-link" href="#demo">Explore Chronos <span aria-hidden="true">↓</span></a></div><p className="lp-hero-note">Built for the people who keep work moving.</p></div>
      <div className="lp-hero-scene"><div className="lp-hero-depth"><div className="lp-orbit lp-orbit-one" /><div className="lp-orbit lp-orbit-two" /><Clock /><div className="lp-hero-preview"><WorkspacePreview /></div><div className="lp-floating-note"><span>✓</span><div><strong>A clearer workday.</strong><small>From first hour to final approval.</small></div></div></div><span className="lp-scene-caption">LESS FRICTION. MORE FLOW.</span></div>
      <a href="#demo" className="lp-scroll-cue"><span>SCROLL TO DISCOVER</span><span aria-hidden="true">↓</span></a></section>
      <div className="lp-capabilities" aria-label="Chronos capabilities"><span>Time, thoughtfully managed.</span><span>PROJECTS</span><span>TIMESHEETS</span><span>APPROVALS</span><span>PEOPLE</span></div>
      <section id="demo" className="lp-demo" ref={demoRef}><div className="lp-demo-sticky">
        <div className="lp-demo-copy"><p className="lp-kicker">01 / A WORKDAY IN CHRONOS</p>
          <div className="lp-chapter-tabs" role="group" aria-label="Explore product demo">{chapters.map((item, i) => <button key={item.label} type="button" aria-pressed={chapter === i} onClick={() => selectChapter(i)}><small>0{i + 1}</small>{item.label}</button>)}</div>
          <div className="lp-copy-stage">{chapters.map((item, i) => <div className="lp-chapter-copy" data-chapter={i} aria-hidden={chapter !== i} key={item.label} style={{ '--layer-opacity': i === 0 ? 1 : 0, '--layer-reveal': i === 0 ? 1 : 0, '--layer-offset': i, '--layer-distance': i }}><h2>{item.title}</h2><p>{item.text}</p></div>)}</div>
          <span className="lp-demo-hint">One continuous story. Scroll to explore.</span><div className="lp-demo-progress" aria-hidden="true"><span /></div>
        </div>
        <div className="lp-demo-scene"><div className="lp-demo-grid" /><div className="lp-depth-orbit lp-depth-orbit-one" aria-hidden="true" /><div className="lp-depth-orbit lp-depth-orbit-two" aria-hidden="true" /><div className="lp-demo-screen">
          {chapters.map((item, i) => <div className="lp-screen-layer" data-chapter={i} aria-hidden={chapter !== i} key={item.label} style={{ '--layer-opacity': i === 0 ? 1 : 0, '--layer-reveal': i === 0 ? 1 : 0, '--layer-offset': i, '--layer-distance': i }}><WorkspacePreview chapter={i} /></div>)}
        </div><div className="lp-depth-floats" aria-hidden="true">{[
          ['32 h', 'Time captured', 'Across your projects'],
          ['03', 'Ready for review', 'Keep the work moving'],
          ['✓', 'Company in sync', 'People, in one place'],
        ].map(([value, label, note], i) => <div className={'lp-depth-card lp-depth-card-' + i} data-chapter={i} key={label} style={{ '--layer-opacity': i === 0 ? 1 : 0 }}><span>{value}</span><div><strong>{label}</strong><small>{note}</small></div></div>)}</div><span className="lp-demo-scene-label">TIME. PROJECTS. PEOPLE. IN SYNC.</span></div>
      </div></section>
      <section id="film" className="lp-film-section"><div className="lp-section-heading"><p className="lp-kicker">02 / THE BIG PICTURE</p><h2>Meet your next<br /><em>workday.</em></h2><p>A closer look at the ideas behind Chronos.<br />A calmer way to keep your company in sync.</p></div><div className="lp-film">
        {promoVideo && !videoError ? <video src={promoVideo} poster={import.meta.env.VITE_PROMO_POSTER_URL || '/media/chronos-promo-poster.png'} controls playsInline preload="metadata" onError={() => setVideoError(true)} aria-label="Chronos promotional video">{import.meta.env.VITE_PROMO_CAPTIONS_URL && <track kind="captions" src={import.meta.env.VITE_PROMO_CAPTIONS_URL} label="English" srcLang="en" default />}</video> : <div className="lp-film-placeholder"><div className="lp-film-orbit" aria-hidden="true" /><span className="lp-film-eyebrow">THE CHRONOS FILM</span><h3>Every hour.<br /><em>A little more human.</em></h3><span className="lp-film-coming">{videoError ? 'The film is temporarily unavailable.' : 'Our story, coming soon.'}</span><span className="lp-film-bottom">CHRONOS BY MAXWELL <span>TIME TO WORK DIFFERENTLY.</span></span></div>}
      </div></section>

      <section className="lp-features"><div className="lp-section-heading"><p className="lp-kicker">03 / THE WHOLE WORKDAY</p><h2>Work has many parts.<br /><em>Give them one home.</em></h2></div><div className="lp-feature-grid">{[
        ['01', 'For your daily work', 'Track project hours, submit expenses and request time off. Spend less time finding the right place to do it.', 'Time · Expenses · Leave'],
        ['02', 'For the people leading it', 'Review submitted work, manage projects and keep company announcements close to the team.', 'Projects · Reviews · Updates'],
        ['03', 'For a connected company', 'Manage people and scoped roles, handle employee requests and keep an audit trail of company activity.', 'People · Access · Audit'],
      ].map(([n, title, text, tags]) => <article key={n}><span className="lp-feature-number">{n}</span><h3>{title}</h3><p>{text}</p><small>{tags}</small></article>)}</div></section>
      <section className="lp-start"><p className="lp-kicker">04 / FIND YOUR FLOW</p><div className="lp-start-heading"><h2>From sign-in<br /><em>to in sync.</em></h2><p>A clear path into your company workspace.</p></div><ol><li><span>01</span><h3>Join your workspace.</h3><p>Sign in with your account, accept your company invitation, or request company access.</p></li><li><span>02</span><h3>Find your projects.</h3><p>Open the company and project tools available to you through your assigned roles.</p></li><li><span>03</span><h3>Make the day count.</h3><p>Capture work, submit requests and follow reviews from one place.</p></li></ol></section>
      <section className="lp-faq"><div><p className="lp-kicker">A FEW MORE THINGS</p><h2>Good questions.<br /><em>Clear answers.</em></h2></div><div>{[
        ['What is Chronos?', 'Chronos is a company workspace for project time, expenses, approvals, time off and employee workflows. The tools you see depend on your company and assigned roles.'],
        ['How do I get access?', 'Existing users can sign in. If you have a company invitation, follow its activation instructions. New companies can use Request company access to start the onboarding process.'],
        ['Can I explore the product before signing in?', 'Yes. The interactive preview above walks through sample time, review and company workflows. It uses illustrative data; your company workspace becomes available after sign-in.'],
        ['Where can I compare plans?', 'The Plans page explains current company plans, included capacity and purchase options. Your Company Admin manages billing for your workspace.'],
      ].map(([q, a], i) => <details key={q}><summary>{q}<span aria-hidden="true">+</span></summary><p>{a}{i === 1 && <Link to="/request-access"> Request company access <Arrow /></Link>}{i === 3 && <Link to="/pricing"> Explore plans <Arrow /></Link>}</p></details>)}</div></section>
      <section className="lp-final"><div className="lp-final-ring" aria-hidden="true" /><p className="lp-kicker">YOUR NEXT CHAPTER STARTS HERE</p><h2>Make room<br />for <em>better work.</em></h2><div className="lp-actions"><Link className="lp-button" to="/login">Get started <Arrow /></Link><Link className="lp-text-link" to="/request-access">Request company access <Arrow /></Link></div><span>CHRONOS · BY MAXWELL</span></section>
    </main><LegalFooter landing />
  </div>;
}
