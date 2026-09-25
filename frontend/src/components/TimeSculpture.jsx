import React, { useState } from 'react';
import '../pages/EmployeeDashboard.css';

export default function TimeSculpture({ variant = 'clock' }) {
  const [paused, setPaused] = useState(false);
  return <div className={`day-sculpture sculpture-${variant} ${paused ? 'is-paused' : ''}`}>
    <div className="day-art" aria-hidden="true">
      <div className="day-art-halo" /><div className="day-art-shadow" />
      <div className="day-clock-float"><div className="day-clock">
        {Array.from({ length: 12 }, (_, i) => <i className="day-clock-layer" key={i} style={{ transform: `translateZ(${i * 2}px)` }} />)}
        <div className="day-clock-face">
          {Array.from({ length: 12 }, (_, i) => <i className="day-clock-tick" key={i} style={{ transform: `rotate(${i * 30}deg)` }}><span /></i>)}
          {variant === 'compass' ? <><span className="compass-north">N</span><span className="compass-east">E</span><span className="compass-south">S</span><span className="compass-west">W</span><i className="compass-needle" /></> : <><span className="day-clock-mark">CHRONOS</span><i className="day-clock-hand hour" /><i className="day-clock-hand minute" /><span className="day-clock-caption">MAKE TIME</span></>}
          <i className="day-clock-pin" />
        </div>
      </div></div>
      <div className="day-art-pearl pearl-one" /><div className="day-art-pearl pearl-two" />
      <span className="day-art-note">{variant === 'compass' ? 'A clear direction. A shared purpose.' : variant === 'team' ? 'Good work starts with a little clarity.' : 'Your time. Well spent.'}</span>
    </div>
    <button className="day-motion-toggle" type="button" onClick={() => setPaused(value => !value)} aria-pressed={paused}>{paused ? 'Play animation' : 'Pause animation'}</button>
  </div>;
}
