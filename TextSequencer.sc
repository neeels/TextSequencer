/* Copyright (c) 2009, 2016 Neels J. Hofmeyr <neels@hofmeyr.de>
 * Published under the GNU GPL v3 or later.
 */

TextSequencer {
    // list must have this pattern:
    //   list = [ [time1, func1], [time2, func2], ... ];
    //   Sort
    // e.g.
    //   list = [ [1, { "hi".postln }],
    //            [5, { "bye".postln }]
    //          ];
    var <>list;

    // Either invoke 'new' without any arguments
    //   x = TextSequencer();
    // or provide parameters as for addString().
    *new {|sounds=nil, string=nil, speed=1, at=0|
        ^super.new.initSequence(sounds, string, speed, at);
    }

    initSequence {|sounds, string, speed, at|
        list = nil;
        this.clear;
        if ((sounds != nil) && (string != nil)){
            this.addString(sounds, string, speed, at);
        };
    }


    addItem {|time, func|
        list.add([time, func]);
    }

    addList {|aList|
        list.addAll(aList);
    }

    add {|sequence|
        list.addAll(sequence.list);
    }

    /* at: an offset to add to each item position.
     * sequence: an other sequence to add the items of.
     * speed: other sequence's positions are divided by speed
     * cutAt: stops adding at this time, so that the open
     *        window is between 'at' and 'cutAt'. 'at' is only a hard
     *        boundary when other sequence's times are >= 0.
     */
    addAt {|at, sequence, speed=1, cutAt=inf|
        var pos;
        if (speed == 1 || speed == 0){
            if ((at == 0) && (cutAt == inf)){
                this.add(sequence);
            }{
                block{|break|
                    sequence.list.do{|item|
                        pos = item[0] + at;
                        if (pos >= cutAt){
                            break.value;
                        };

                        list.add([pos, item[1]]);
                    };
                };
            };
        }{
            block{|break|
                sequence.list.do{|item|
                    pos = item[0]/speed + at;
                    if (pos >= cutAt){
                        break.value;
                    };

                    list.add([pos, item[1]]);
                };
            };
        };
    }

    append {|sequence, measure=16, speed=1|
        var start = 0;
        if (list.size > 0){
            start = list[list.size - 1][0];
            start = measure * ((start / measure).trunc + 1);
        };
        this.addAt(start, sequence, speed);
    }


    clear {
        if (list != nil){
            list.free;
            list = nil;
        };
        list = SortedList.new(0, {|item1, item2| item1[0] < item2[0]});
    }


    addStringInternal {|sounds, string, speed=1, at=0, doCut=true|
        var item, count, total,
            tree, level, idx, isopen,
            pos,
            queue, cutAt;

        // first count the elements of each bracket, counting every
        // subracket as one entry in its parent.
        // Record them in a tree structure of bracket levels
        // and order of occurence within each level.
        tree = List();
        tree.add(List[0]); // level 0
        level = 0;
        idx = 0;

        string.do{|ch|
            if ((ch == $[ ) || (ch == $( )){
                // an open bracket. First count it.
                tree[level].put(idx, tree[level].at(idx) + 1);
                // one level deeper, make a new counter.
                level = level + 1;
                // need a new level node?
                if (tree.size < (level + 1)){
                    // make a new level node.
                    tree.add(List());
                };
                // a level exists, append a new zero entry
                tree[level].add(0);
                // focus the new one
                idx = tree[level].size - 1;
            }{
              if ((ch == $] ) || (ch == $) )){
                  // close bracket, one level back
                  if (level > 0){
                      level = level - 1;
                      // focus the last one
                      idx = tree[level].size - 1;
                  };
              }{
                  // not a bracket.
                  if (((ch == $  ) || (ch == $\t)).not){
                      // it's a real char entry. count it.
                      tree[level].put(idx, tree[level].at(idx) + 1);
                  };
              };
            };
        }; // string.do

        // now go through the string again, with the prior knowledge
        // of the bracket sizes to calculate the speed increase within.
        // All items are first accumulated in queue.
        level = 0;
        idx = 0;
        pos = at;
        queue = SortedList(0, {|ia,ib| ia[0] < ib[0]});
        string.do{|ch|
            if ((ch == $[ ) || (ch == $( )){
                // an open bracket. Step into it.
                level = level + 1;
                // focus the first one
                idx = 0;
                // how many elements?
                count = tree[level].at(idx);

                // speed up
                if (count > 0){
                    speed = speed * count;
                };
            }{
              if ((ch == $] ) || (ch == $) )){
                  // close bracket, one level back
                  if (level > 0){
                      // how many elements?
                      count = tree[level].at(idx);
                      // remove this entry.
                      tree[level].removeAt(idx);

                      // step out one level
                      level = level - 1;
                      // focus the first one
                      idx = 0;

                      // slow down
                      if (count > 0){
                          speed = speed / count;
                      };
                  };
              }{
                  // not a bracket.
                  if (((ch == $  ) || (ch == $\t )).not){
                      // it's a real char entry. evaluate it.
                      item = sounds.at(ch.asString);
                      if (item != nil){
                          queue.add([pos, item]);
                      };
                      pos = pos + (1/speed);
                  };
              };
            };
        }; // string.do

        // we could just add the queue now, but we're going to check
        // whether there are special items to expand in this queue.
        queue.do{ |item, i|
            if (item[1].class == TextSequencer){
                cutAt = inf;
                // should we cut the sequence?
                if (doCut && ((i+1) < queue.size)){
                    cutAt = queue[i+1][0];
                };
                // add the whole sequence
                this.addAt(item[0], item[1], cutAt: cutAt);
            }{
                // add just one item
                this.addItem(item[0], item[1]);
            }
        };
        queue.clear;

        // return the total number of time steps (seconds if speed==1).
        ^tree[0].at(0)
    }

    /* sounds: a list of functions (or other TextSequencer instances)
     * doCut: if a sound entry is a TextSequencer, then expand that
     *        sequence only up to the next item's time.
     * string: may be either just a string or an array of strings,
     *         in which case this acts as if the function was called
     *         multiple times.
     */
    addString {|sounds, string, speed=1, at=0, doCut=true|
        if (string.class == Array){
             string.do{ |realString|
                 this.addStringInternal(sounds, realString, speed, at, doCut);
             };
        }{
             this.addStringInternal(sounds, string, speed, at, doCut);
        };
    }

    addLoop {|times, sequence, width, offset=0, speed=1|
        times.do{|idx|
            this.addAt(offset + (idx*width), sequence, speed:speed);
        };
    }

    play {|server, speed=1, latency=0.05, start=0, doloop=0|
        var playRoutine;
        playRoutine = Routine{
            var now, pos, time, func, waitTime, go;

            block{|break|
            loop{
                if (start == nil){
                    now = list[0][0] / speed; // time of first item
                }{
                    now = start / speed;
                };

                list.do{|item|
                    time = item[0] / speed;
                    func = item[1];
                    if (time >= now){
                        waitTime = time - now;
                        if (waitTime > 1e-2){
                            waitTime.wait;
                            now = now + waitTime;
                        };
                        server.makeBundle(latency, { func.value(now); });
                    };
                };

                if (doloop <= 0){
                    break.value;
                }{
                    waitTime = (doloop / speed) *
                               (1.0 -
                                  ( now / ( doloop/speed ) ).frac
                                );
                    if (waitTime > 1e-2){
                        waitTime.wait;
                    };
                };
            }};

        };
        playRoutine.play;
        ^playRoutine;
    }

}

