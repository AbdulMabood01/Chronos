import {useCurrentFrame, useVideoConfig} from 'remotion';
import {Frame, Heading, Card, Rise, Pill, colors} from './design';
export const LeaveScene = () => {
  const f=useCurrentFrame(); const {fps}=useVideoConfig(); const approved=f>1.35*fps;
  return <Frame chapter="04 / LEAVE">
    <Heading eyebrow="PLAN YOUR TIME OFF" sub="Request leave and follow its status in one place.">Time off.<br/><span style={{color:colors.teal}}>Less back-<br/>and-forth.</span></Heading>
    <Rise delay={.1} style={{position:'absolute',right:120,top:245,width:730}}><Card>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}><div style={{fontSize:38,fontWeight:700}}>Vacation request</div><Pill active={approved}>{approved ? 'Approved' : 'Pending'}</Pill></div>
      <div style={{fontSize:28,color:'#607184',marginTop:25}}>A little space to recharge.</div>
      <div style={{display:'grid',gridTemplateColumns:'repeat(7, 1fr)',gap:12,marginTop:35}}>{Array.from({length:21},(_,i)=><div key={i} style={{height:60,display:'flex',alignItems:'center',justifyContent:'center',borderRadius:12,fontSize:27,background:i>=8&&i<=12?'#0f7c90':'#e7f1f5',color:i>=8&&i<=12?'white':'#607184'}}>{i+1}</div>)}</div>
      <div style={{marginTop:30,fontSize:32,fontWeight:600}}>5 days to switch off.</div>
      <div style={{marginTop:15,fontSize:26,color:'#607184'}}>{approved ? 'You’re all set. Enjoy your break.' : 'Sent for manager review.'}</div>
    </Card><div style={{color:colors.muted,fontSize:20,marginTop:20,textAlign:'right'}}>Illustrative workflow · Demo data</div></Rise>
  </Frame>;
};
