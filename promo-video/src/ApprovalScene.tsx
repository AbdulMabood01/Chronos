import {Frame, Heading, Card, Rise, Pill, colors} from './design';
export const ApprovalScene = () => <Frame chapter="05 / APPROVALS">
  <Heading eyebrow="BRING THE TEAM TOGETHER" sub="Review time, leave, and expenses. Keep decisions clear.">Keep work<br/><span style={{color:colors.teal}}>moving.</span></Heading>
  <div style={{position:'absolute',right:120,top:235,width:730}}>
    <Rise delay={.1}><Card style={{marginBottom:25}}><div style={{fontSize:25,color:'#607184',letterSpacing:3}}>TIMESHEET</div><div style={{fontSize:38,fontWeight:650,marginTop:14,marginBottom:24}}>Workweek submitted</div><Pill>Ready for review</Pill></Card></Rise>
    <Rise delay={.3}><Card><div style={{fontSize:25,color:'#607184',letterSpacing:3}}>TIME OFF</div><div style={{fontSize:38,fontWeight:650,marginTop:14,marginBottom:24}}>Vacation request</div><Pill>Approved</Pill></Card></Rise>
    <Rise delay={.55}><div style={{fontSize:30,color:colors.teal,marginTop:32}}>Clear status. A connected team.</div></Rise>
    <div style={{color:colors.muted,fontSize:20,marginTop:20,textAlign:'right'}}>Illustrative workflow · Demo data</div>
  </div>
</Frame>;
