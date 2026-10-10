import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {Card, Frame, Heading, Pill, Rise, colors} from './design';
export const TimeScene = () => {
  const f = useCurrentFrame(); const {fps} = useVideoConfig();
  return <Frame chapter="01 / TIME">
    <Heading eyebrow="TRACK YOUR WORK" sub="Log hours. Submit timesheets. Track status.">Every hour.<br/><span style={{color:colors.teal}}>In view.</span></Heading>
    <Rise delay={.1} style={{position:'absolute', right:120, top:245, width:730}}><Card>
      <div style={{fontSize:32, color:'#607184'}}>Your workweek</div>
      <div style={{display:'flex', justifyContent:'space-between', alignItems:'center', marginTop:18}}><div style={{fontSize:66, fontWeight:700}}>40<span style={{fontSize:30, color:'#607184'}}> hours</span></div><Pill>Submitted</Pill></div>
      <div style={{display:'flex', gap:20, alignItems:'end', height:220, marginTop:28}}>{['MON','TUE','WED','THU','FRI'].map((d,i)=><div key={d} style={{flex:1, textAlign:'center'}}><div style={{fontSize:28, marginBottom:12, color:'#075e6d'}}>8h</div><div style={{height:interpolate(f,[(.15+i*.06)*fps,(.55+i*.06)*fps],[0,168],{extrapolateLeft:'clamp',extrapolateRight:'clamp'}), borderRadius:'12px 12px 3px 3px', background:i===4 ? '#4f46e5':'#0f7c90'}}/><div style={{fontSize:23,marginTop:18,color:'#607184'}}>{d}</div></div>)}</div>
      <div style={{marginTop:42,paddingTop:28,borderTop:'1px solid #c8dce3',fontSize:28,color:'#607184'}}>One clear record of your time.</div>
    </Card><div style={{color:colors.muted,fontSize:20,marginTop:20,textAlign:'right'}}>Illustrative workflow · Demo data</div></Rise>
  </Frame>;
};
