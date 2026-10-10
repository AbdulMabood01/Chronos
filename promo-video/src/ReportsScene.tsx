import {Frame, Heading, Card, Rise, colors} from './design';
export const ReportsScene = () => <Frame chapter="07 / REPORTING">
  <Heading eyebrow="TURN RECORDS INTO REPORTS" sub="Review project summaries. Export approved timesheets and leave records.">Ready for<br/><span style={{color:colors.teal}}>the next step.</span></Heading>
  <div style={{position:'absolute',right:120,top:245,width:730}}>
    <Rise delay={.1}><Card><div style={{fontSize:42,fontWeight:700}}>Project timesheets</div><div style={{fontSize:27,color:'#607184',marginTop:16}}>Choose. Review. Export.</div><div style={{marginTop:32}}>{[['Alex Lee','40 h'],['Jordan Morgan','38 h'],['Sam Taylor','40 h']].map(([name,hours])=><div key={name} style={{display:'flex',alignItems:'center',gap:20,fontSize:29,padding:'24px 0',borderTop:'1px solid #c8dce3'}}><span style={{color:'#0f7c90',fontSize:32}}>☑</span><span style={{flex:1}}>{name}</span><strong>{hours}</strong></div>)}</div><div style={{background:'#4f46e5',color:'white',borderRadius:14,padding:22,textAlign:'center',fontSize:31,fontWeight:650}}>↓ Export approved timesheets</div></Card></Rise>
    <div style={{color:colors.muted,fontSize:20,marginTop:20,textAlign:'right'}}>Illustrative workflow · Demo data</div>
  </div>
</Frame>;
