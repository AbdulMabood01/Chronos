import {Frame, Heading, Card, Pill, Rise, colors} from './design';
export const ExpensesScene = () => <Frame chapter="03 / EXPENSES">
  <Heading eyebrow="FROM RECEIPT TO REVIEW" sub="Submit claims with receipts. Follow budgets and approval status.">Expenses.<br/><span style={{color:colors.teal}}>Accounted for.</span></Heading>
  <div style={{position:'absolute',right:120,top:230,width:730}}>
    <Rise delay={.1}><Card><div style={{fontSize:28,color:'#607184'}}>PROJECT / WEBSITE LAUNCH</div><div style={{fontSize:44,fontWeight:700,marginTop:22}}>Travel expense</div><div style={{fontSize:86,fontWeight:700,margin:'15px 0 22px'}}>$248<span style={{fontSize:42}}>.00</span></div><Pill active={false}>Submitted for review</Pill><div style={{marginTop:30,background:'#e6f1f5',padding:'22px 28px',borderRadius:16,fontSize:28}}>▤ &nbsp; Travel receipt attached</div></Card></Rise>
    <Rise delay={.45}><div style={{marginTop:28,borderLeft:`5px solid ${colors.teal}`,paddingLeft:24,fontSize:33}}>Claims. Receipts. Project budgets.</div></Rise>
    <div style={{color:colors.muted,fontSize:20,marginTop:20,textAlign:'right'}}>Illustrative workflow · Demo data</div>
  </div>
</Frame>;
