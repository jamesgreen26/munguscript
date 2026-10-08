# MungusScript

The script command language from Zero Point Systems, as a plain Java library. It depends on
[Brigadier](https://github.com/Mojang/brigadier) and nothing else: no Minecraft, no mod loader.

A script is a sequence of single-line commands. Each one reads a value with a getter, transforms
it with mappers, and hands it to an executor:

```
#def text = read_file
write_file "hello"
write_file value_of(read_file + " world")
if text lines > 0 write_file value_of(text <+ "first line\\n")
```

## Layout

- `g_mungus.munguscript.language`: what scripts are made of, and all that code extending the
  language needs. Build nodes with `ScriptNodes` and types with `ScriptType.writable` or
  `ScriptType.opaque`, and register them with a `ScriptRegistrar`. The built-in types (`int`,
  `double`, `string`, `boolean`, in `BuiltInTypes`) and the mappers between them are here too.
  Depends on nothing in `engine`.
- `g_mungus.munguscript.engine`: what a host implements and calls to build, parse and run the
  language. Start from `MungusScript` and implement `ScriptHost`, or only `ScriptViewHost` for a
  program that parses and suggests but never runs, such as a client. `engine.codec` encodes the
  engine's tree on a server and decodes it into a view on a client; the host writes only its own
  argument types and applicabilities (`HostCodec`).
- `g_mungus.munguscript.runner`: runs whole scripts in one place, for a host that needs no
  dispatcher or graft of its own. `ScriptRunner` builds the engine, runs a script line by line
  until one fails, and returns what ran and what stopped it (`ScriptResult`, `ScriptProblem`),
  located by line. `SimpleHost` and `SimpleSource` are a ready-made host and source, so a program
  with no command source of its own only supplies its context:

  ```java
  ScriptRunner<SimpleSource> runner = ScriptRunner.builder("my_app").register(MyNodes::register).build();
  ScriptResult result = runner.run(script, new SimpleSource(myContext));
  ```
- `g_mungus.munguscript.engine_impl`: the engine itself. Internal: the `g_mungus.munguscript`
  module does not export it, and `MungusScript` reaches it as the `EngineProvider` the module
  provides. On the classpath (no module system) it is internal by convention only.
- `g_mungus.munguscript.debug`: a runner for running a script from a file, printing what stopped
  it. Not exported: it is a way to try the language, not an API.

## Design docs

- `design_docs/SCRIPT_ENGINE_RESPONSIBILITIES.md`: what the library does, and what it leaves to
  the host.
- `design_docs/SCRIPT_LANGUAGE_CONSTRAINTS.md`: what the language cannot do under the execution
  model it runs in.
- `design_docs/SCRIPT_ENGINE_TREE.md`: how the engine builds its Brigadier tree, traced through
  one line.

## Running a script

```
./gradlew run                                  # runs run/main.munguscript
./gradlew run --args=path/to/other.munguscript
```

Commands run in order until one fails. Besides the built-ins, the debug runner has `read_file` and
`write_file`, on `output.txt` next to the script.

## Building and testing

```
./gradlew build
```

The tests in `g_mungus.munguscript.conformance` run scripts through the whole system using only
the `language` and `engine` packages, and run once against every `EngineProvider` registered on
the test classpath. To try another engine implementation, list its provider class in
`src/test/resources/META-INF/services/g_mungus.munguscript.engine.spi.EngineProvider` and every
conformance test runs against it too. `PackageDependencyTest` keeps those tests, and the
`engine` API, from using `engine_impl`.

Requires Java 17 or newer.
