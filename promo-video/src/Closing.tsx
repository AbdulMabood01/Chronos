import {Frame, Rise, colors} from './design';
export const Closing = () => <Frame chapter="MAKE TIME FOR YOUR TEAM">
  <div style={{position:'absolute',inset:0,display:'flex',flexDirection:'column',alignItems:'center',justifyContent:'center'}}>
    <Rise><div style={{fontSize:52,color:colors.muted,letterSpacing:1}}>Your team. Your time.</div></Rise>
    <Rise delay={.3}><div style={{fontSize:210,fontWeight:700,letterSpacing:-10,lineHeight:1.2}}>Chronos<span style={{color:colors.teal}}>.</span></div></Rise>
    <Rise delay={.35}><div style={{fontSize:42,color:colors.muted,marginTop:12}}>Your workforce. In sync.</div></Rise>
    <Rise delay={.55}><div style={{fontSize:38,fontWeight:650,color:colors.ink,background:colors.teal,padding:'23px 50px',borderRadius:100,marginTop:55}}>Meet Chronos →</div></Rise>
  </div>
</Frame>;
