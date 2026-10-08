# MungusScript — Responsibilities

*What the library is responsible for, and what it leaves to the host that embeds it. The
execution model the language has to work under is in `SCRIPT_LANGUAGE_CONSTRAINTS.md`, and how
the engine builds its tree and runs a line in it is in `SCRIPT_ENGINE_TREE.md`.*

## 1. Overview

| | Library | Host |
|---|---|---|
| **Is** | A plain Java library on Brigadier | The program embedding it |
| **Knows about** | Types, nodes, the grammar, one command line at a time | Its world: what targets are, where values come from, who is writing scripts |
| **Defines** | The type and node model, and the contracts listed in §5 | Its own types and nodes, and implementations of those contracts |
| **Runs** | One command line against a source the host supplies; through the runner, a script top to bottom in one place | Whole scripts: order, timing, repetition, where lines come from |

The library never decides anything about the host's world. Wherever its behaviour depends on the
world (which target a command is aimed at, what an argument means at a place), it asks the host
through a contract. Wherever the host needs language behaviour (parsing, suggestions, failure
text), it calls the library instead of reimplementing it.

## 2. What the Library Owns

The library is split by package. The **Model** below is `g_mungus.munguscript.language`, which is
all that code extending the language needs and depends on nothing else in the library. Everything
from **Language** on is `g_mungus.munguscript.engine`, which depends on it.

### Model

- **Types.** A type is a key (`namespace:path`), a Java class, the argument that reads it, a
  resolver from what the argument parsed to the value, and a text form that reads back what it
  prints. An *opaque* type has no argument or text form; it only comes out of getters and
  mappers.
- **Nodes.** Getters produce a value, mappers turn one value into another, argument mappers do
  so using an argument written after them, and executors act on a value. These four kinds are
  the whole model.
- **Built-ins.** The types `int`, `double`, `string` and `boolean`, in the `script` namespace,
  and the mappers between them: comparisons and arithmetic, rounding, string joining and lines,
  `&&` and `||`.
- **Generated mappers.** `as_string` and `==` for every writable type. Nothing reads a string as
  another type unless the host registers a mapper for it.
- **Conversions.** A type declares the types it is usable as (`ScriptType.usableAs`), and every
  type is usable as a string. A value is converted wherever one of those is wanted, and their
  mappers can follow it, without a word in the script.
- **Replacement.** A host mapper with the same name and input type as a built-in or generated
  one replaces it.

### Language

- **The grammar.** Getter → mappers → executor chains, `if` / `unless` / `else`,
  `value_of(...)`, argument placeholders (`%s`) and executor overloads.
- **Building.** Collecting one build's registrations, working out which mappers can reach which
  types, and building the Brigadier nodes: the executor tree, the condition chain and a
  `value_of` dispatcher per type.
- **Execution semantics.** Per-run state (current value, condition, pending `else` branch,
  `value_of` result); evaluating each `value_of(...)` in a run of its own; and the overload
  rule: the first variant that matches its target explicitly, otherwise the first unrestricted
  one, otherwise none.
- **Pre-processing.** Running a chain of pre-processors over command text before parsing, and
  composing their source maps so ranges still point at the text as written. The alias
  pre-processor (`#def name = expression`) ships with the library.

### Feedback

- **Failures.** Turning whatever a command threw into a reason, the command as written, the
  command as run, and the range at fault in the text as written. Faults inside a `value_of(...)`
  or an expansion are located in the enclosing text.
- **Diagnostics.** Why a `value_of(...)` did not give the type wanted: what it gave, how far it
  got, or which word is not known.
- **Suggestions.** What Brigadier's suggestion API returns for script arguments, `value_of`
  contents and pre-processor tokens. A node with applicability is only suggested where the host
  says it applies; it still parses and runs everywhere.
- **Views.** Parsing, suggesting and probing without running anything, both over a tree the
  library built and over one that was built elsewhere and sent to the host.
- **Tree codec.** Encoding the tree under the graft, with its argument types and restrictions,
  and decoding it into a view on a client (`SCRIPT_TREE_CODEC.md`).

### Runner

- **Script runner.** For a host that runs scripts in the same place it writes them: builds the
  engine and hosts it in a dispatcher of its own, pre-processes and runs a script's lines in
  order, stops at the first line with a problem, and returns what ran and what stopped it,
  located by line. Built only on the engine's API; a host whose scripts cross a channel
  (`SCRIPT_LANGUAGE_CONSTRAINTS.md`) uses the engine directly.
- **Simple host.** A ready-made `ScriptHost` and command source for a program with no source of
  its own. The host supplies only its context, a default namespace and, if it has targets, a
  matcher.

### Debug Runner

- **Debug runner.** A script runner with nodes that read and write a file, which runs a script
  file and prints what stopped it. It is how the library is tried out without an embedding
  program.

## 3. What the Library Guarantees

- **Dependencies.** Brigadier and the JDK only.
- **No global state.** Each build is a separate engine instance. Engines can coexist, and a
  rebuild never changes an engine in place.
- **Opaque host objects.** The library never looks inside the host context, applicability or
  build environment. It only passes them back to the host's own code.
- **Run state stays internal.** Only the library creates or reads run state. The host only
  carries it.
- **Stable tree.** The names of the library's own nodes are fixed, so a view can find the tree
  again wherever it is sent. Its nodes compare equal only if they also redirect to the same
  place, so anything that deduplicates nodes keeps same-named branches apart.
- **One command at a time.** The engine runs nothing outside the command it was handed. Only the
  runner, when a host asks it to run a script, runs one line after another, and nothing more.
- **Failures as data.** No logging, no listeners, no formatted output. Failure text is plain
  strings, worded for the person who wrote the script, never an exception name or a stack trace.

## 4. What the Host Owns

- **Where the tree lives.** Which dispatcher the library's nodes are grafted into, what prefix
  leads to them, and when the engine is rebuilt.
- **The command source.** A source type that carries the library's run state and the host's
  context through every copy.
- **Content.** Every type and node beyond the built-ins, and what applicability each node has.
- **Targets.** What a target is, and whether a node is meant for the one a run is aimed at.
- **Its own pre-processors.** Rewrites that need the host's context, and the order of the chain.
- **Running scripts.** Sequencing, timing, waiting, repetition, retries and persistence, unless
  running a script top to bottom in one place, as the runner does, is all it needs.
- **Presentation.** How failures, diagnostics, suggestions and highlighting are shown, whether
  failures are logged, and whether they are kept.
- **Delivery.** Moving the encoded tree to wherever a view is made, and writing its own argument
  types and `Applicability` objects inside it (`HostCodec`).

## 5. Contracts

Each contract is defined by the library and implemented or filled in by the host.

| Contract | The library | The host |
|---|---|---|
| `MungusScript.engine` | Builds an engine from the host and its registrations | Calls it on each build, with its `BuildEnvironment` |
| `ScriptViewHost` | Asks a view's host for the host context, target matches and a default namespace | Implements them; types in its default namespace are written by path |
| `ScriptHost` | Also asks a host that runs scripts for run state | Implements `ScriptViewHost` and the run-state methods |
| `ScriptRegistrar` | Collects one build; fails if a node uses an unregistered type | Registers its types and nodes; keys must be unique |
| `ScriptType`, `TypeKey` | Builds arguments from a type's literal form, resolves values with the run's context, prints and parses values | Defines its types with `ScriptType.writable` or `opaque`; `parse` must read what `print` prints |
| `BuildEnvironment` | Passes it to argument factories | Puts in whatever its argument types need |
| `ScriptEngine.graft`, `ScriptView.scriptRoot`, `ScriptEngine.begin` | Builds nodes for the host's source type, and finds the script root again in a received tree | Grafts them, redirects to the script root, calls `begin` in the redirect |
| `ScriptHost.runState`, `withRunState`, `hostContext`; `RunState` | Creates and reads run state | Carries it, and the host context, on the source |
| `ScriptContext` | Hands node functions the host context and the resolved argument | Reads its own context from it |
| `Applicability`, `ScriptViewHost.match`, `Match` | Asks about restricted nodes; applies the overload rule; suggests a restricted node only where it applies | Attaches applicability; answers *explicit*, *unrestricted* or *no match* |
| `CommandPreProcessor`, `Rewriter`, `SourceMap` | Chains steps in the host's order (`CommandPreProcessor.chain`) and composes source maps | Prepares against the script; rewrites commands with a source map and typed tokens |
| `ExpressionProbe` | Answers whether text reads as a type, without running it | Uses it in pre-processors, e.g. to check an expansion |
| `ScriptFailure` | Describes a failure, located in the text as written | Shows, logs or keeps it |
| `ScriptTreeCodec`, `HostCodec` | Encodes the tree under the graft, with its argument types and the engine's restrictions, and decodes it into a view on the client | Moves the bytes, and writes and reads its own argument types and `Applicability` objects |
| `MungusScript.view`, `ScriptEngine.restrictions`, `Restriction` | Rebuilds a view from a received tree, the host's types and the engine's restrictions | For a host with a tree codec of its own: delivers the tree intact, and the restrictions with it, serialising each `Applicability` its own way |
| `MungusScript.arguments`, `ScriptArguments.Rebuild` | Describes its own argument types for sending; rebuilds them for one received tree, and points them at the view it makes over that tree. Fails if they are used before the view exists, or if the view is made over a tree they are not in | Sends the descriptions with its tree, rebuilds each with `Rebuild.argument` while decoding, and makes the view with `Rebuild.view` once the tree is decoded |

## 6. Not the Library's Job

- Sequencing scripts beyond running them top to bottom in one place: waiting, delays,
  repetition, retries, and anything that crosses a channel.
- Anything visible: screens, colours, formatted messages.
- Persisting anything.
- Deciding which targets a node is for, or what a target is.
- Moving bytes between processes. The library encodes and decodes its tree (`ScriptTreeCodec`);
  the host carries the bytes.

## 7. Open Questions

- **Overload policy.** The library keeps "explicit before unrestricted". If a host could want a
  different rule, the matcher could rank overloads instead.
- **Waiting.** Is it part of the language, as a statement the library parses and the host
  carries out, or purely the host's?
