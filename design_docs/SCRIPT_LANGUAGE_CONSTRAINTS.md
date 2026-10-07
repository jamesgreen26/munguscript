# Script Language — Constraints

*What the script language cannot do because of the execution model it runs under. A host that
runs scripts differently is not bound by these, but anything the engine adds must still work,
or be refused, under them.*

## 1. Execution Model

Three roles take part in running a script:

- The **sequencer** holds the script. Each step it takes one line, pre-processes it (aliases,
  then any rewrites that need its own context) and sends the result as text.
- The **channel** carries that text to every receiver attached to it.
- Each **runner** on the channel receives the text and runs it as one command against its own
  target.

The sequencer moves to the next line after a fixed delay, or after an explicit wait. Starting a
run and looping are decisions the sequencer makes on its own.

## 2. Where Values Exist

- Values exist only at a runner. Getters read the runner's target, and the sequencer has no
  target.
- The sequencer never evaluates anything. Its only call into the engine is a parse-only check
  that a condition reads as a boolean, made while expanding aliases.
- So any construct that needs a value has to be resolved entirely within the one line a runner
  runs.

## 3. One-Way Control

- Nothing comes back from a runner to the sequencer: no result, no failure, no return code.
- The sequencer cannot branch, loop or stop based on anything a line computed.
- **Not possible:** `while`, an `if` that chooses the next line, loops that end on a condition,
  try/catch, abort on failure, exit codes, input.
- **Possible:** anything the sequencer decides without a value, such as labels/`goto`,
  `repeat N`, `stop`, procedures included as text, and parameterized aliases.

## 4. Each Line Stands Alone

- A runner runs each line with a fresh command source. No state carries from one line to the
  next.
- **Not possible:** variables shared across lines, or values that outlive their line.
- The closest workable form is a store kept on each runner. With several runners that means
  several separate stores, and the sequencer still cannot read them.
- Aliases are text macros. They run their getters again each time they are used and hold no
  value.

## 5. One Line, Many Targets

- Several runners can share one channel. The same line then runs against each runner's target,
  and each run has its own outcome.
- A line has no single result, even in principle.
- A block `if` can only be lowered into per-line conditions. Each copy checks the condition
  again, a step apart and separately at each runner.

## 6. Only Text Crosses the Channel

- Only the rewritten command text travels: no source map, no original line, no metadata.
- A receiver takes at most 3600 characters, so expansions must fit.
- The channel carries one message at a time, so nothing can travel alongside the command as a
  second message.
- Receivers other than runners may read the same text, and a runner may be unable to run while
  one of them is attached.
- Lines need not come from a sequencer. Anything that sends text can drive a runner, so a runner
  cannot assume pre-processing state exists.

## 7. Context Is Split

- Rewrites that need the sequencer's context, such as relative positions or named locations,
  happen at the sequencer, before sending.
- Alias definitions come from the top of the script, which only the sequencer has.
- A runner cannot redo any of these. Pre-processing has to stay in the sequencer.

## 8. Failure Reporting

- Failures happen at a runner, which has only the expanded text, so a runtime fault can only
  point at the expanded command.
- Positions in the text as written are available only where pre-processing and evaluation happen
  in the same place, such as an editor checking a script, a runner evaluating an expression it
  stores itself, or a host that runs scripts in one place.
- A failure is seen at the runner that ran the line, not at the sequencer.

## 9. Output

- When running commands, a runner's only effect is the executor acting on its target.
- A runner can instead evaluate one stored expression at a fixed interval and send the result
  on the channel as text. This is the only way a value leaves a runner, and it goes to whatever
  reads the channel, not back to a sequencer.
- A general `print` has nowhere to go other than the runner itself.

## 10. What This Leaves Open

- Anything inside a single line: literal values, the standard library of mappers, more types,
  and more operators.
- Anything the sequencer can do as text: aliases with parameters, includes, unconditional jumps
  and repeats.
- Any construct that needs value-dependent control flow or state across lines means changing
  where the interpreter runs, not extending the language.
