## Testing Tansit implementations

This project contains code which may be used to test all
implementations of transit.

To run a test, execute the following command in the `transit-format`
directory.

```
bin/verify -impls clj -enc json
```

This will test the `json` version of the `transit-clj` implementation.

The `-enc` option can be either `json` or `msgpack`. If it is omitted
then both with be tested.

The `-impls` option can be any languange implementation. `clj` will
test `transit-clj` and `ruby` will test `transit-ruby`. If this option
is omitted then it will attempt to test all implementations. Multiple
languages can be specified.

```
bin/verify -impls clj ruby
```

To test everything run:

```
bin/verify
```

There is also a `-gen` option to run generative tests. The following usage

```
bin/verify -gen 100
```

will generate 100 random edn structures. There are still some issues
around equality testing that need to be fixed to eliminate false
positives.

Timing information can also be collected for each implementation.

```
bin/verify -time
```

### Test requirements

Testing `transit-clj` requires Maven.

Testing `transit-java` requires Maven.

Testing `transit-ruby` requires Ruby >= 1.9 and Bundler `gem install bundler`.

Testing `transit-jruby` requires JRuby >= 1.7.13 and Bundler `gem install bundler`.


### Supporting testing

The only requirement for an implementation to be testable is that it
have a script named `roundtrip` in its `bin` directory. This script
must start a process which accepts transit data on standard in and
returns transit data on standard out. The process should read the
transit data, then write it to standard out.

The script must take one argument which can be either `json`,
`json-verbose`, or `msgpack` which will be used to setup the correct
encoding.


### Building the exemplars

To build the exemplars, simply make sure the software is up to date
and run the script bin/exemplars. This will generate a large number of
example files in examples/0.8/simple. By convention we keep the latest
versions of the examples files checked in.
