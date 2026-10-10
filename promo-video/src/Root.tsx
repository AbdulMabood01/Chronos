import {Composition, Folder} from 'remotion';
import {ChronosPromo} from './ChronosPromo';
import {Opening} from './Opening';
import {TimeScene} from './TimeScene';
import {LeaveScene} from './LeaveScene';
import {ApprovalScene} from './ApprovalScene';
import {Closing} from './Closing';
import {ProjectsScene} from './ProjectsScene';
import {ExpensesScene} from './ExpensesScene';
import {PeopleScene} from './PeopleScene';
import {ReportsScene} from './ReportsScene';

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition id="ChronosPromo" component={ChronosPromo} durationInFrames={900} fps={30} width={1920} height={1080} />
      <Folder name="Scenes">
        <Composition id="Projects" component={ProjectsScene} durationInFrames={110} fps={30} width={1920} height={1080} />
        <Composition id="Expenses" component={ExpensesScene} durationInFrames={110} fps={30} width={1920} height={1080} />
        <Composition id="People" component={PeopleScene} durationInFrames={110} fps={30} width={1920} height={1080} />
        <Composition id="Reports" component={ReportsScene} durationInFrames={110} fps={30} width={1920} height={1080} />
        <Composition id="Opening" component={Opening} durationInFrames={110} fps={30} width={1920} height={1080} />
        <Composition id="Time" component={TimeScene} durationInFrames={110} fps={30} width={1920} height={1080} />
        <Composition id="Leave" component={LeaveScene} durationInFrames={110} fps={30} width={1920} height={1080} />
        <Composition id="Approvals" component={ApprovalScene} durationInFrames={110} fps={30} width={1920} height={1080} />
        <Composition id="Closing" component={Closing} durationInFrames={100} fps={30} width={1920} height={1080} />
      </Folder>
    </>
  );
};
