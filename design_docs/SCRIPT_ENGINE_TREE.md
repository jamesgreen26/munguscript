# MungusScript — How a Line Runs

*How the engine turns registered types and nodes into a Brigadier tree, and what happens to one
script line in it. What the library owes the host is in `SCRIPT_ENGINE_RESPONSIBILITIES.md`.*

## 1. Why a Tree

The engine has no parser or interpreter of its own. It builds Brigadier nodes from what was
registered, and the host grafts them into its own dispatcher. From then on Brigadier does the work:

- **Parsing and suggesting** are Brigadier walking the tree. Errors and suggestions come from the
  same nodes that run.
- **Running** happens through *redirects*. Most of the engine's nodes redirect to another node and
  carry a redirect modifier, which Brigadier calls as it passes through. Each modifier is one step
  of the line (`NodeActions`, `Step`).
- **State** travels on the command source, because Brigadier passes nothing else from node to
  node. The host carries a `RunState` on its source (`ScriptHost.withRunState`): a `CommandRun`
  for a script command, a `ValueRun` for a `value_of(...)`. Every node reads the state first and
  does nothing without the kind it expects, so nothing runs outside a run.

A line such as `read_file lines > 0` is not evaluated by walking an AST. Brigadier parses it
through a chain of redirects, and running the parse applies each step in order.

## 2. The Layout

The engine grafts these nodes under the node the host chose (`NodeNames` holds the names,
`ScriptTree` reads them back):

```
munguscript:script               where a line starts: every executor, plus if and unless
munguscript:condition            getters that can lead to a boolean, after if / unless
munguscript:condition/<type>     mappers from <type> that can still lead to a boolean;
                                 for boolean, a copy of every executor, followed by else
munguscript:value                every getter, inside value_of(...)
munguscript:value/<type>         every mapper from <type>
```

**Chain nodes** are the core idea. `munguscript:value/script:int` means "the value so far is an
int". A getter redirects to the chain node of the type it gives. A mapper is a child of the chain
node for its input and redirects to the chain node for its output. What can follow a value is just
the children of its chain node, and a chain of any length is a walk through a fixed number of
nodes: there is one chain node per type, not one per chain.

**The chain node's name carries its type.** A view finds the types of getters and mappers from the
names their redirects point at (`ScriptTree.typeAfter`), so a client that received the tree knows
them without the server's registry. The view over a received tree and the engine's own view run
the same code.

**Conditions and `value_of` use separate chains**, because a condition's boolean is followed by
executors and a `value_of`'s value is followed by nothing. Condition chains exist only for types
that can still reach a boolean (`TypeGraph`), so a condition that can never become one fails while
it parses. Value chains hold every mapper; whether a `value_of` gives the type wanted is checked
by reading it (§5).

**Executors are built twice.** Under `munguscript:script` they end the line. Under
`munguscript:condition/script:boolean` each one may be followed by its own `else`, which leads back
to `munguscript:script`. So `else` parses only after a branch that has a condition, and the last
branch of a chain ends the line.

**Argument nodes are named by their hint** (`int`, `string`). Executors that share a name share one
argument node, whose type (`OverloadedArgument`) holds one slot per variant. Each slot is a
`ValueOrLiteralArgument`, which reads the type's own literal, a whole `value_of(...)` or a `%s`
placeholder.

## 3. One Line, Parsed

The debug host (`./gradlew run`) enters scripts as `run <line>`. Take this line:

```
if read_file lines > 0 write_file value_of(read_file + " world") else write_file "empty"
```

Brigadier parses it into one context per redirect. In order (paths under the grafted node,
`munguscript:` written as `m:`):

| Text | Node | Redirects to |
|---|---|---|
| `run` | the host's own literal | `m:script` |
| `if` | `m:script/if` | `m:condition` |
| `read_file` | `m:condition/read_file` | `m:condition/script:string` |
| `lines` | `…/script:string/lines` | `m:condition/script:int` |
| `>` | `…/script:int/>` | (continues to its argument) |
| `0` | `…/script:int/>/int` | `m:condition/script:boolean` |
| `write_file` | `…/script:boolean/write_file` | (continues to its argument) |
| `value_of(read_file + " world")` | `…/write_file/string`, executable | (continues to `else`) |
| `else` | `…/write_file/string/else` | `m:script` |
| `write_file` | `m:script/write_file` | (continues to its argument) |
| `"empty"` | `m:script/write_file/string`, executable | |

Two things to notice:

- `value_of(read_file + " world")` is **one token** in the command, but it is **checked as it
  parses**: the slot reads the expression through the view and fails the parse if it cannot give a
  string (§5). `write_file value_of(read_file lines)` does not parse at all: "value_of(read_file
  lines) gives int, but write_file needs string".
- The first `write_file` is under `m:condition/script:boolean` and can take `else`. The last one is
  under `m:script` and cannot: the same executor, built in two places.

## 4. The Same Line, Run

Brigadier runs the redirect modifiers in the order above, then the command of the last node. The
host's modifier on `run` calls `ScriptEngine.begin`, which puts a fresh `CommandRun` on the source.

| Step | What runs | `CommandRun` after it |
|---|---|---|
| `if` | `NodeActions.startCondition` | conditional, not negated |
| `read_file` | `NodeActions.conditionStep` | condition value = the file's text |
| `lines` | `conditionStep` | condition value = its line count |
| `> 0` | `conditionStep` | condition value = `true` or `false` |
| `else` | `NodeActions.elseBranch` for `write_file` | runs the first `write_file` if the condition held, marking a branch as run; then starts an unconditional branch |
| `"empty"` | `NodeActions.executor` for `write_file` | if a branch already ran, returns its result; otherwise runs the last `write_file` |

So `else` works by **running the branch before it as it passes**. Brigadier only runs the command
at the end of the line, so the branch before an `else` would never run on its own. Each `else` node
is built for one executor and runs that executor's branch (`CommandRun.runBranch`). Once a branch
has run, conditions after it are not worked out, and every later branch returns its result, so a
chain returns the result of the one branch that ran, or 0.

The `value_of(...)` is evaluated only when its executor actually runs. If the condition fails,
`read_file + " world"` is never read from the file.

## 5. Inside value_of

A `value_of(...)` is read **in place**: its expression is parsed from where it starts in the command
to its closing bracket, with a dispatcher whose root holds the getters of `m:value`
(`ExpressionReader`). Every position in that parse, including those of nested `value_of`s, is a
position in the command, so failures need no translating.

It is read twice:

1. **While the command parses**, the slot asks the view to read it (`ViewArguments.check`). The
   view parses the expression without a source and looks at where it stopped: if it ended at a
   chain node of a type the slot takes, it reads; otherwise the reason is a syntax error at the
   `value_of(` (`ValueOfException`). A nested `value_of` is checked by its own slot during this
   parse, and its explanation is passed outward.
2. **When its executor or mapper runs**, `Evaluator` copies the source with a fresh `ValueRun`,
   reads the expression again, and runs the parse.

For `read_file + " world"`, where the slot takes a string:

| Text | Node | Redirects to |
|---|---|---|
| `read_file` | `m:value/read_file`, executable | `m:value/script:string` |
| `+` | `…/script:string/+` | (continues to its argument) |
| `" world"` | `…/+/string`, executable | `m:value/script:string` |

There is no terminal node. Every step of a value chain has both a redirect, for when more follows,
and a command (`NodeActions.lastValueStep`), for when it is last. Brigadier follows a redirect only
when there is more to read, so the last step's command runs, applies the step and leaves the result
in the `ValueRun`. The earlier steps run as redirect modifiers (`NodeActions.valueStep`).

When an expression cannot be used, the reader says why from where it stopped (`Shape`): it is
empty, starts with an unknown word, needs an argument it does not have, has a word that cannot
follow its type, or gives the wrong type.

## 6. Overloads

Executors that share a name share one argument node. Its type (`OverloadedArgument`) tries each
variant's slot and keeps the variants that read the most of the command, so `go 5 6` is read as a
point rather than an int followed by junk. A `value_of` counts as reading for a variant only if it
gives that variant's type, so it chooses between overloads just as a literal of that type would.

When the command runs, `Overloads.choose` asks the host to match each kept variant and runs the
first *explicit* match, otherwise the first *unrestricted* one, otherwise none. None is not a
failure: the command does nothing and returns 0.

## 7. Where to Look

| To change | Start at |
|---|---|
| The layout, or a new kind of node | `TreeBuilder`, `NodeNames`, `ScriptTree` |
| What a step does when it runs | `NodeActions`, `Step`, `CommandRun`, `ValueRun` |
| How `value_of(...)` is read, checked or explained | `ValueOrLiteralArgument`, `ExpressionReader`, `Shape`, `Evaluator` |
| Which types a chain can reach | `TypeGraph` |
| Which overload runs | `OverloadedArgument`, `Overloads` |
| What is suggested | `Suggester`, `RestrictionIndex` |
| Checking registrations | `Registry`, `NodeTypes` |
| Finding the tree again on a client | `ScriptTree.find`, `ArgumentLookup`, `ScriptArgumentsImpl` |
