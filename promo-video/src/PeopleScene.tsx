import {Frame, Heading, Card, Rise, colors} from './design';
export const PeopleScene = () => <Frame chapter="06 / YOUR TEAM">
  <Heading eyebrow="A CONNECTED WORKSPACE" sub="Manage people and roles. Share announcements. Stay notified.">Your people.<br/><span style={{color:colors.teal}}>Connected.</span></Heading>
  <div style={{position:'absolute',right:120,top:230,width:730}}>
    <Rise delay={.1}><Card><div style={{fontSize:34,fontWeight:700}}>The right roles for your team.</div>{[['AL','Alex Lee','Employee'],['JM','Jordan Morgan','Project Manager']].map(([initials,name,role],i)=><div key={name} style={{display:'flex',alignItems:'center',gap:22,paddingTop:26,marginTop:20,borderTop:'1px solid #c8dce3'}}><div style={{width:64,height:64,borderRadius:50,background:i?'#e5e3ff':'#ccf5e8',display:'grid',placeItems:'center',fontSize:25,fontWeight:700,color:'#075e6d'}}>{initials}</div><div style={{fontSize:31,fontWeight:650}}>{name}<div style={{fontSize:25,fontWeight:400,color:'#607184',marginTop:5}}>{role}</div></div></div>)}</Card></Rise>
    <Rise delay={.45}><Card style={{marginTop:24,padding:30,background:'#d6f8f0'}}><div style={{fontSize:27,color:'#075e6d',letterSpacing:2}}>NEW ANNOUNCEMENT</div><div style={{fontSize:32,fontWeight:650,marginTop:10}}>Keep everyone in the loop.</div></Card></Rise>
    <div style={{color:colors.muted,fontSize:20,marginTop:20,textAlign:'right'}}>Illustrative workflow · Demo data</div>
  </div>
</Frame>;
