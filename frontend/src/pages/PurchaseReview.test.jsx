// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import {afterEach,beforeEach,it,expect,vi} from 'vitest';
import {render,screen,fireEvent,cleanup} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import PurchaseReview from './PurchaseReview';
const {api,workspace}=vi.hoisted(()=>({api:{purchase:vi.fn(),summary:vi.fn(),checkout:vi.fn()},workspace:{currentCompany:{id:12,name:'Atlas'}}}));
vi.mock('../api',()=>({billingAPI:api}));vi.mock('../CompanyContext',()=>({useCompany:()=>workspace}));
afterEach(cleanup);
beforeEach(()=>{vi.clearAllMocks();workspace.currentCompany={id:12,name:'Atlas'};api.purchase.mockResolvedValue({data:{id:'q1',status:'QUOTED',plan_key:'PRO',term_months:3,extra_seats:0,amount_cents:44700,subtotal_cents:44700,discount_cents:0,starts_at:'2026-10-09',ends_at:'2027-01-09',expires_at:'2099-01-01'}});api.summary.mockResolvedValue({data:{checkoutEnabled:false}});});
const open=()=>render(<MemoryRouter initialEntries={['/billing/payment?quote=q1']}><PurchaseReview/></MemoryRouter>);
it('allows review without checkout and offers a return path without taking payment',async()=>{open();await screen.findByRole('heading',{name:'Online payment is not available yet'});expect(screen.getByText(/Pay once: \$447.00/)).toBeInTheDocument();expect(screen.getByRole('link',{name:'Return to billing'})).toHaveAttribute('href','/billing');expect(api.checkout).not.toHaveBeenCalled();});
it('requires terms acceptance and preserves the review after a checkout failure',async()=>{api.summary.mockResolvedValue({data:{checkoutEnabled:true}});api.checkout.mockRejectedValue({response:{data:{message:'Checkout is unavailable'}}});open();const pay=await screen.findByRole('button',{name:'Continue to secure payment'});expect(pay).toBeDisabled();fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(pay);expect(await screen.findByRole('alert')).toHaveTextContent('Checkout is unavailable');expect(api.checkout).toHaveBeenCalledWith(12,'q1','2026-10-09');expect(screen.getByRole('region',{name:'Payment review'})).toBeVisible();});
it('does not offer payment on an expired quote',async()=>{api.summary.mockResolvedValue({data:{checkoutEnabled:true}});const value=await api.purchase();api.purchase.mockResolvedValue({data:{...value.data,expires_at:'2000-01-01'}});open();expect(await screen.findByRole('alert')).toHaveTextContent('quote has expired');expect(screen.queryByRole('button',{name:'Continue to secure payment'})).not.toBeInTheDocument();});
