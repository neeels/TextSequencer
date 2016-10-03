/*
 * TextSequencer is a SuperCollider class.
 * Copyright (c) 2009,2016 Neels J. Hofmeyr <neels@hofmeyr.de>
 * Published under the GNU GPL v3 or later.
 *
 * Write simple strings to compose complex rhythms.
 *
 * So with TextSequencer, you compose a list of time events (a TextSequencer).
 * You can collapse any number of Sequences into a single resulting
 * TextSequencer, which is then played using only one running Routine.
 */

(
 Quarks.install("TextSequencer");
 thisProcess.platform.recompile;
)

 If you'd like to use your local git checkout at e.g.'~/git/TextSequencer'
 instead:

(
 Quarks.install("~/git/TextSequencer");
 thisProcess.platform.recompile;
)

(
// just two synths for example playback.

SynthDef("blip", {|i_out = 0, freq|
    var out;
    out = SinOsc.ar(freq, mul:0.3);
    out = out * EnvGen.ar(Env.perc(0.01,0.5), doneAction:2);
    Out.ar(i_out, Pan2.ar(out));
}).send(s);

SynthDef("blop", {|i_out = 0, freq|
    var out;
    out = Saw.ar(Line.kr(freq, 10, 0.3), mul:0.6);
    out = out * EnvGen.ar(Env.perc(0.01,0.1), doneAction:2);
    Out.ar(i_out, Pan2.ar(out));
}).send(s);

)


// Use string patterns to compose rhythms.
(
 var sounds, seq;


 // First, build a sound dictionary of characters mapping to
 // functions; note supercollider's $x notation for single characters:
 sounds = Dictionary[
        "a"->{ |now| ("a" + now).postln; Synth(\blip, [\freq, 220]); },
        "b"->{ |now| ("b" + now).postln; Synth(\blip, [\freq, 330]); },
        "c"->{ |now| ("c" + now).postln; Synth(\blip, [\freq, 440]); },

        "o"->{ |now| ("o" + now).postln; Synth(\blop, [\freq, 440*5/12]); },
        "p"->{ |now| ("p" + now).postln; Synth(\blop, [\freq, 440*7/12]); }
        ];


 // Now use these characters in a string. Each character represents
 // one time step (1.0). Anything in brackets is compressed into one
 // time step, i.e. evenly distributed in that time step.
 // '[]' and '()' brackets are not distinguished, whatever suits you.
 // Any undefined character serves as a pause, any *space is ignored*.

 // Doing it while creating a new instance:
 seq = TextSequencer(sounds, "abc. abc. a[b..b]c[ba] .[ab]c");

 // And adding something to an existing sequence (brackets can nest):
 seq.addString(sounds,  ".p.o .(pp).(oo) .((pp).).((oo).) .op.");

 // Let's add something to after the end to illustrate brackets.
 seq.addString(sounds,
               "(pppppppppp)(pppppppp)(pppppp)(pppp)(ppp)(pp)ppp.p.o",
               at: 16);

 // Play at double speed, meaning 2 time steps per second.
 // For a penguin farm playing pong, set this to 20.
 ~routine = seq.play(s, 2);
)

~routine.stop;
(
~routine.reset;
~routine.play;
)


// Use numbers and functions to compose rhythms.
(
 var seq, reel;

 // Have a few functions that do stuff when they are called.
 // In this case they both print and play something.
 a = { |now| ("a " + now).postln; Synth(\blip, [\freq, 220]); };
 b = { |now| ("b " + now).postln; Synth(\blip, [\freq, 440]); };
 c = { |now| ("c " + now).postln; Synth(\blip, [\freq, 330]); };

 // Here's a blank sequence instance:
 seq = TextSequencer();

 // ...and here we add some entries. The number is the time index
 // and the letter is one of the functions defined just above.
 seq.addList([ [0,a], [1,b], [1.5,b], [2,c]]);
 // This is already play-back-able:
 //seq.play(s);

 // ...but let's repeat this pattern a few times into a new TextSequencer:

 reel = TextSequencer();

 // add it once at the start,
 reel.add(seq);

 // another at time 4
 reel.addAt(4, seq);

 // and another at time 8, this time twice as fast.
 reel.addAt(8, seq, speed:2);

 // The sequence is completely composed in 'reel'. Playback,
 // for fanciness speeding it up by three sevenths:

 ~routine = reel.play(server:s, speed:(10/7), latency:0.3);

 // Note that the printouts appear 'latency' seconds earlier
 // than the sounds are played.
 // And note that the times printed correspond to the actual
 // resulting time after all the speeds are applied.
 // And that this 'done' message prints before everything else:
 "done".postln;
)

// While it's still playing, you can stop the sequence by using
// the return value from play(). It's a Routine.
~routine.stop;

// You can at any time restart that Routine to replay the same thing
// by calling these two:
(
~routine.reset;
~routine.play;
)


