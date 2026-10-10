import {useRef} from 'react';
import Icon from './Icon';
import './DateInput.css';
export default function DateInput({pickerLabel='Open calendar',readOnly=false,disabled=false,...props}) {
 const input=useRef(null);
 function open(){if(readOnly||disabled)return;input.current?.focus();try{input.current?.showPicker?.();}catch{/* Native input remains available as a fallback. */}}
 return <div className="date-input-control"><input {...props} ref={input} type="date" readOnly={readOnly} disabled={disabled}/><button type="button" className="date-picker-button" aria-label={pickerLabel} title={readOnly?'Date is locked':pickerLabel} disabled={readOnly||disabled} onClick={open}><Icon name="calendar" size={18}/></button></div>;
}
