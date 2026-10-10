import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {Card, Frame, Heading, Rise, colors} from './design';
export const ProjectsScene = () => {
  const f=useCurrentFrame(); const {fps}=useVideoConfig();
  return <Frame chapter="02 / PROJECTS">
    <Heading eyebrow="CONNECT TIME TO WORK" sub="Assign your team. Track logged and planned hours.">Projects.<br/><span style={{color:colors.teal}}>In focus.</span></Heading>
    <div style={{position:'absolute',right:120,top:230,width:730}}>
      <Rise delay={.1}><Card><div style={{fontSize:28,color:'#607184',letterSpacing:3}}>PROJECT OVERVIEW</div><div style={{fontSize:46,fontWeight:700,marginTop:20}}>Website launch</div><div style={{display:'flex',gap:80,marginTop:30}}><div><strong style={{fontSize:64}}>128</strong><div style={{fontSize:26,color:'#607184'}}>Hours logged</div></div><div><strong style={{fontSize:64,color:'#0f7c90'}}>160</strong><div style={{fontSize:26,color:'#607184'}}>Hours planned</div></div></div><div style={{background:'#d7e8ee',height:24,borderRadius:15,marginTop:32,overflow:'hidden'}}><div style={{background:'#0f7c90',height:'100%',width:`${interpolate(f,[.25*fps,1.2*fps],[0,80],{extrapolateLeft:'clamp',extrapolateRight:'clamp'})}%`}}/></div></Card></Rise>
      <Rise delay={.35}><Card style={{marginTop:26,padding:30,display:'flex',alignItems:'center',gap:28}}><div style={{fontSize:48,color:'#4f46e5'}}>↗</div><div style={{fontSize:30,fontWeight:650}}>People, projects, and hours.<div style={{fontSize:25,fontWeight:400,color:'#607184',marginTop:8}}>A shared view of the work.</div></div></Card></Rise>
      <div style={{color:colors.muted,fontSize:20,marginTop:20,textAlign:'right'}}>Illustrative workflow · Demo data</div>
    </div>
  </Frame>;
};
