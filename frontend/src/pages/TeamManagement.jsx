import React from 'react';
import ScreenTitle from '../components/ScreenTitle';
import MissingTimesheets from '../components/MissingTimesheets';
import TeamLeaveCalendar from '../components/TeamLeaveCalendar';

export function MissingTimesheetsPage() {
  return <div className="page-container">
    <div className="header-bar"><div>
      <ScreenTitle title="Missing timesheets" icon="clock" eyebrow="MANAGEMENT" />
      <p className="page-subtitle">Track outstanding and approved submissions across your active projects.</p>
    </div></div>
    <MissingTimesheets />
  </div>;
}

export function TeamLeaveCalendarPage() {
  return <div className="page-container">
    <div className="header-bar"><div>
      <ScreenTitle title="Team leave calendar" icon="calendar" eyebrow="MANAGEMENT" />
      <p className="page-subtitle">Plan around approved leave and team availability.</p>
    </div></div>
    <TeamLeaveCalendar />
  </div>;
}
