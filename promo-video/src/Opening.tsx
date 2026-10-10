import {useCurrentFrame, useVideoConfig, interpolate} from 'remotion';
import {Frame, Rise, colors} from './design';
export const Opening = () => {
  const f = useCurrentFrame(); const {fps} = useVideoConfig();
  return <Frame chapter="WORK, IN SYNC">
    <div style={{position: 'absolute', width: 530, height: 530, right: 155, top: 265, border: '2px solid #6cf8e850', borderRadius: '50%', boxShadow: '0 0 130px #33dacb18'}}>
      <div style={{position:'absolute', inset: 34, border:'1px solid #ffffff20', borderRadius:'50%'}} />
      {Array.from({length:12}, (_,i) => <div key={i} style={{position:'absolute', left:260, top:25, width:4, height:22, background: i % 3 === 0 ? colors.teal : '#ffffff40', transformOrigin:'2px 240px', rotate:`${i*30}deg`}} />)}
      <div style={{position:'absolute', left:260, top:100, width:10, height:165, background:colors.teal, borderRadius:12, transformOrigin:'center bottom', rotate:`${interpolate(f,[0,3*fps],[0,220])}deg`}} />
      <div style={{position:'absolute', left:260, top:145, width:10, height:120, background:colors.indigo, borderRadius:12, transformOrigin:'center bottom', rotate:'120deg'}} />
      <div style={{position:'absolute', left:248, top:248, width:34, height:34, borderRadius:'50%', background:'#f3fbff'}} />
    </div>
    <div style={{position:'absolute', left:120, top:260, width:1100}}>
      <Rise><div style={{fontSize:144, fontWeight:700, lineHeight:1.04, letterSpacing:-7}}>Make time<br/>for what <span style={{color:colors.teal}}>matters.</span></div></Rise>
      <Rise delay={.25}><div style={{fontSize:44, color:colors.muted, marginTop:45}}>Time. Projects. People. In sync.</div></Rise>
    </div>
  </Frame>;
};
