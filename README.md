# adamant-run

A Clojure library to adamantly (or stubbornly) try to run a function 

## Usage

You can pass any clojure function to `adamant-run` along with its
arguments and some finetuning options to control how adamant-run will
run your function.

The easiest case is calling a function with no arguments using the
defaults of `adamant-run`:

```clojure
 (adamant-run (fn [] "OK"))
```

... more documentation to be written as soon as the API stabilizes. 


## License

Copyright (C) 2011 Stefan Kamphausen

Distributed under the Eclipse Public License, the same as Clojure.
See file COPYING.
