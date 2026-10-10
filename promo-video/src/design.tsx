import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig} from 'remotion';

export const colors = {teal: '#33dacb', indigo: '#9692ff', ink: '#071c29', muted: '#a7c0cb'};
export const Frame: React.FC<{children: React.ReactNode; chapter?: string}> = ({children, chapter}) => {
  const f = useCurrentFrame();
  return <AbsoluteFill style={{background: colors.ink, color: '#f3fbff', fontFamily: 'Segoe UI, Arial, sans-serif', overflow: 'hidden'}}>
    <AbsoluteFill style={{background: 'radial-gradient(ellipse at 85% 25%, #284268 0%, transparent 55%), radial-gradient(ellipse at 0% 100%, #0d5b60 0%, transparent 55%)'}} />
    <AbsoluteFill style={{opacity: .12, backgroundImage: 'linear-gradient(#81b3c5 1px, transparent 1px), linear-gradient(90deg, #81b3c5 1px, transparent 1px)', backgroundSize: '96px 96px', translate: `0px ${f / 8}px`}} />
    <div style={{position: 'absolute', left: 120, top: 70, fontSize: 32, letterSpacing: 6, fontWeight: 700}}>CHRONOS<span style={{color: colors.teal}}> /</span></div>
    {chapter && <div style={{position: 'absolute', right: 120, top: 78, fontSize: 25, color: colors.muted, letterSpacing: 4}}>{chapter}</div>}
    <AbsoluteFill>{children}</AbsoluteFill>
    <div style={{position:'absolute', left:0, top:0, height:5, width:1920, background:colors.teal, opacity:.6, translate:`${interpolate(f,[0,110],[-1920,0],{extrapolateRight:'clamp'})}px 0px`}} />
    <div style={{position: 'absolute', bottom: 60, left: 120, right: 120, height: 2, background: '#ffffff16'}} />
  </AbsoluteFill>;
};

export const Rise: React.FC<{children: React.ReactNode; delay?: number; style?: React.CSSProperties}> = ({children, delay = 0, style}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  return <div style={{...style, opacity: interpolate(frame, [delay * fps, (delay + .2) * fps], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}), translate: `0px ${interpolate(frame, [delay * fps, (delay + .32) * fps], [55, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'})}px`}}>{children}</div>;
};
export const Heading: React.FC<{eyebrow: string; children: React.ReactNode; sub: string}> = ({eyebrow, children, sub}) => <div style={{position: 'absolute', left: 120, top: 245, width: 820}}>
  <Rise><div style={{color: colors.teal, fontSize: 30, letterSpacing: 5, fontWeight: 600, marginBottom: 35}}>{eyebrow}</div></Rise>
  <Rise delay={.15}><div style={{fontSize: 112, fontWeight: 700, letterSpacing: -5, lineHeight: 1.05}}>{children}</div></Rise>
  <Rise delay={.4}><div style={{fontSize: 42, lineHeight: 1.4, color: colors.muted, marginTop: 40, maxWidth: 700}}>{sub}</div></Rise>
</div>;
export const Card: React.FC<{children: React.ReactNode; style?: React.CSSProperties}> = ({children, style}) => <div style={{background: '#f3fafc', color: '#172033', borderRadius: 32, boxShadow: '0 36px 100px #0005', padding: 42, ...style}}>{children}</div>;
export const Pill: React.FC<{children: React.ReactNode; active?: boolean}> = ({children, active = true}) => <span style={{display: 'inline-block', borderRadius: 100, background: active ? '#ccf5e8' : '#e7e8ff', color: active ? '#116c52' : '#5146a4', padding: '12px 24px', fontSize: 27, fontWeight: 700}}>{children}</span>;
