// @vitest-environment jsdom
import React from 'react';import {afterEach,expect,it,vi} from 'vitest';import {cleanup,render,screen,fireEvent} from '@testing-library/react';import DateInput from './DateInput';
afterEach(()=>{cleanup();vi.restoreAllMocks();});
it('opens the native calendar from the visible calendar button',()=>{render(<DateInput id="date" pickerLabel="Choose date"/>);const input=document.getElementById('date');input.showPicker=vi.fn();fireEvent.click(screen.getByRole('button',{name:'Choose date'}));expect(input.showPicker).toHaveBeenCalledOnce();});
it('keeps the calendar button visible but unavailable for a locked date',()=>{render(<DateInput id="date" pickerLabel="Choose date" readOnly/>);const button=screen.getByRole('button',{name:'Choose date'});expect(button.disabled).toBe(true);expect(document.getElementById('date').readOnly).toBe(true);});
