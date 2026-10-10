"""Original 144 BPM newsroom cue. No samples or external dependencies."""
import math, random, wave
from array import array
from pathlib import Path
SR = 44100
BEAT = 60 / 144
rng = random.Random(144)
mix = array('f', [0]) * (SR * 30)
def hz(midi):
    return 440 * 2 ** ((midi - 69) / 12)
def add(start, length, voice, freq=0, gain=1):
    first = int(start * SR)
    for j in range(max(0, min(int(length * SR), len(mix)-first))):
        t=j/SR
        envelope=min(1,t/.008)*min(1,(length-t)/.045)
        if voice=='kick':
            v=math.sin(2*math.pi*(46*t+3.4*(1-math.exp(-t*27))))*math.exp(-t*12)
            v+=rng.uniform(-1,1)*math.exp(-t*190)*.25
        elif voice=='snare':
            v=(rng.uniform(-1,1)*.7+math.sin(2*math.pi*180*t)*.3)*math.exp(-t*20)
        elif voice=='hat':
            v=rng.uniform(-1,1)*math.exp(-t*85)
        elif voice=='bass':
            v=(math.sin(2*math.pi*freq*t)+.28*math.sin(4*math.pi*freq*t)+.12*math.sin(6*math.pi*freq*t))*math.exp(-t*3)
        elif voice=='brass':
            v=sum(math.sin(2*math.pi*freq*k*t)/k for k in range(1,7))*math.exp(-t*2.8)
        elif voice=='string':
            v=(math.sin(2*math.pi*freq*t)+.4*math.sin(2*math.pi*freq*1.003*t)+.18*math.sin(4*math.pi*freq*t))*min(1,t/.16)
        elif voice=='riser':
            v=rng.uniform(-1,1)*(t/length)**2*.5+math.sin(2*math.pi*(200*t+850*t*t/length))*.15*t/length
        else:
            v=rng.uniform(-1,1)*math.exp(-t*3)
        mix[first+j]+=v*envelope*gain
roots=[38,34,36,33]
for beat in range(72):
    t=beat*BEAT
    root=roots[(beat//8)%4]
    add(t,.32,'kick',gain=.65)
    if beat%2: add(t,.22,'snare',gain=.28)
    add(t,.075,'hat',gain=.095)
    add(t+BEAT/2,.055,'hat',gain=.065)
    add(t,BEAT*.8,'bass',hz(root),.32)
    add(t+BEAT/2,BEAT*.4,'bass',hz(root+(12 if beat%4==3 else 0)),.17)
    if beat%8 in (0,3,6):
        third=3 if root==38 else 4
        for midi in (root+12,root+12+third,root+19): add(t,.42,'brass',hz(midi),.095)
    if beat%8==0:
        for midi in (root+24,root+31): add(t,BEAT*7.8,'string',hz(midi),.04)
        add(t,.8,'crash',gain=.08)
        if beat>0: add(t-.3,.3,'riser',gain=.16)
    if beat>=64 and beat%2: add(t+BEAT*.75,.12,'snare',gain=.14)
add(29.58,.4,'kick',gain=.7)
peak=max(abs(v) for v in mix)
scale=.88/peak
pcm=array('h')
for i,value in enumerate(mix):
    t=i/SR
    fade=max(0,min(1,t/.035,(30-t)/.18))
    left=value*scale*fade
    right=.94*left+.06*mix[max(0,i-317)]*scale*fade
    pcm.extend((int(left*32767),int(right*32767)))
out=Path(__file__).parent/'public'/'chronos-newsroom.wav'
with wave.open(str(out),'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print(f'{out}: 30s, 144 BPM, stereo 44.1 kHz, peak below -1 dBFS')
