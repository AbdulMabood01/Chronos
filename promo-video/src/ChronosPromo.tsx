import {staticFile, useVideoConfig} from 'remotion';
import {TransitionSeries, linearTiming} from '@remotion/transitions';
import {slide} from '@remotion/transitions/slide';
import {wipe} from '@remotion/transitions/wipe';
import {Audio} from '@remotion/media';
import {Opening} from './Opening';
import {TimeScene} from './TimeScene';
import {LeaveScene} from './LeaveScene';
import {ApprovalScene} from './ApprovalScene';
import {Closing} from './Closing';
import {ProjectsScene} from './ProjectsScene';
import {ExpensesScene} from './ExpensesScene';
import {PeopleScene} from './PeopleScene';
import {ReportsScene} from './ReportsScene';
export const ChronosPromo = () => {
  const {fps} = useVideoConfig();
  return <><Audio src={staticFile('chronos-newsroom.wav')} volume={0.85} premountFor={fps} /><TransitionSeries>
    <TransitionSeries.Sequence name="Make time" durationInFrames={110} premountFor={fps}><Opening /></TransitionSeries.Sequence>
    <TransitionSeries.Transition presentation={wipe({direction:'from-left'})} timing={linearTiming({durationInFrames:10})} />
    <TransitionSeries.Sequence name="Track time" durationInFrames={110} premountFor={fps}><TimeScene /></TransitionSeries.Sequence>
    <TransitionSeries.Transition presentation={slide({direction:'from-right'})} timing={linearTiming({durationInFrames:10})} />
    <TransitionSeries.Sequence name="Manage projects" durationInFrames={110} premountFor={fps}><ProjectsScene /></TransitionSeries.Sequence>
    <TransitionSeries.Transition presentation={wipe({direction:'from-bottom'})} timing={linearTiming({durationInFrames:10})} />
    <TransitionSeries.Sequence name="Expenses and receipts" durationInFrames={110} premountFor={fps}><ExpensesScene /></TransitionSeries.Sequence>
    <TransitionSeries.Transition presentation={slide({direction:'from-left'})} timing={linearTiming({durationInFrames:10})} />
    <TransitionSeries.Sequence name="Request leave" durationInFrames={110} premountFor={fps}><LeaveScene /></TransitionSeries.Sequence>
    <TransitionSeries.Transition presentation={wipe({direction:'from-right'})} timing={linearTiming({durationInFrames:10})} />
    <TransitionSeries.Sequence name="Review approvals" durationInFrames={110} premountFor={fps}><ApprovalScene /></TransitionSeries.Sequence>
    <TransitionSeries.Transition presentation={slide({direction:'from-bottom'})} timing={linearTiming({durationInFrames:10})} />
    <TransitionSeries.Sequence name="People and announcements" durationInFrames={110} premountFor={fps}><PeopleScene /></TransitionSeries.Sequence>
    <TransitionSeries.Transition presentation={wipe({direction:'from-left'})} timing={linearTiming({durationInFrames:10})} />
    <TransitionSeries.Sequence name="Report exports" durationInFrames={110} premountFor={fps}><ReportsScene /></TransitionSeries.Sequence>
    <TransitionSeries.Transition presentation={slide({direction:'from-right'})} timing={linearTiming({durationInFrames:10})} />
    <TransitionSeries.Sequence name="Meet Chronos" durationInFrames={100} premountFor={fps}><Closing /></TransitionSeries.Sequence>
  </TransitionSeries></>;
};
