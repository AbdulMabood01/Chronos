"""Generate a quiet original electronic bed; no external samples or dependencies."""
import math
import wave
from array import array
from pathlib import Path

rate = 22050
seconds = 30
notes = ((130.81, 164.81, 196.0), (110.0, 130.81, 164.81),
         (87.31, 110.0, 130.81), (98.0, 123.47, 146.83))
data = array('h')
for i in range(rate * seconds):
    t = i / rate
    chord = notes[int(t / 3) % 4]
    beat = t % .5
    phrase = t % 3
    pad_envelope = min(1, phrase / .25) * min(1, (3 - phrase) / .3)
    pad = sum(math.sin(2 * math.pi * n * t) for n in chord) * .07 * pad_envelope
    pluck_note = chord[int(t / .5) % 3] * 4
    pluck = math.sin(2 * math.pi * pluck_note * t) * math.exp(-beat * 10) * .13
    kick = math.sin(2 * math.pi * (48 * beat + 3 * (1 - math.exp(-beat * 18)))) * math.exp(-beat * 18) * .14
    fade = min(1, t / 1.2, (seconds - t) / 1.8)
    data.append(int(22000 * (pad + pluck + kick) * max(0, fade)))
out = Path(__file__).parent / 'public' / 'chronos-bed.wav'
with wave.open(str(out), 'wb') as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(rate)
    w.writeframes(data.tobytes())
print(out)
