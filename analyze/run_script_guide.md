# How to Use `run.sh`

A convenience script to compile and run any Java solution in the 1brc project without needing to type the full `javac`/`java` commands manually.

## Location

```
/home/cuongnn/projects/1brc/run.sh
```

## Usage

From the project root (`/home/cuongnn/projects/1brc`):

```sh
./run.sh <JavaClassName>
```

The script expects the Java file to exist at:
```
src/main/java/dev/morling/onebrc/<JavaClassName>.java
```

## Examples

```sh
# Run SlowForLoop.java
./run.sh SlowForLoop

# Run CalculateAverage_one.java
./run.sh CalculateAverage_one
```

## What the script does

1. Validates that you provided a class name argument.
2. Checks that the `.java` file exists under `src/main/java/dev/morling/onebrc/`.
3. Compiles the file with `javac`.
4. Runs the compiled class with `java -cp src/main/java`.

## Notes

- Always run the script from the project root directory.
- The class name is case-sensitive and must match the filename exactly (without `.java`).
- If compilation fails, the script exits immediately and does not attempt to run.
---

# How to Use `profile.sh` (flamegraph profiling)

Runs a solution with [async-profiler](https://github.com/async-profiler/async-profiler) attached via jbang and the `ap-loader` agent. When the run finishes, you get a flamegraph HTML file showing where the program spends its time.

## Prerequisites

- [jbang](https://www.jbang.dev/) installed and on your `PATH`.
- On the first run, jbang downloads `ap-loader` from Maven Central, so it needs network access.

## Usage

From the project root:

```sh
./profile.sh <JavaClassName> [event]
```

- `JavaClassName`: same as `run.sh`. The file must exist at `src/main/java/dev/morling/onebrc/<JavaClassName>.java`.
- `event` (optional, default `cpu`): the async-profiler event to sample.
  - `cpu`: where CPU time goes
  - `alloc`: where memory is allocated (useful for finding GC pressure)
  - `lock`: contended locks
  - `wall`: wall-clock time, including time spent blocked or waiting on I/O

## Examples

```sh
# CPU flamegraph for EasySolution -> profile-EasySolution-cpu.html
./profile.sh EasySolution

# Allocation flamegraph -> profile-EasySolution-alloc.html
./profile.sh EasySolution alloc
```

## What the script does

It runs this command (with the class name and event filled in):

```sh
jbang --javaagent=ap-loader@jvm-profiling-tools/ap-loader=start,event=cpu,file=profile-<JavaClassName>-cpu.html \
    src/main/java/dev/morling/onebrc/<JavaClassName>.java
```

jbang compiles and runs the source file directly, so you don't need to run `javac` first. The output file name includes the class and the event, so profiles from different runs don't overwrite each other.

## Reading the flamegraph

Open the HTML file in a browser, for example `xdg-open profile-EasySolution-cpu.html`.

- Each box is a method. The x-axis width is the share of samples, so wider means more time. Left-to-right order is alphabetical and does not mean time order.
- The y-axis is the call stack. Callers are below and callees are above.
- Click a box to zoom into it. Use Ctrl+F to search for and highlight a method, such as `HashMap` or `parseDouble`.
- Look for wide plateaus at the top of the stacks. Those are the methods that are actually burning the time.

## Notes

- Profiling adds a small overhead, so use `run.sh` for timing numbers and `profile.sh` to find where the time goes.
- The generated `profile-*.html` files are build artifacts. You probably don't want to commit them.
