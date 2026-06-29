# Command-code codegen

**English** · [简体中文](README.zh-CN.md)

NativeRelay routes requests by an `int` **command** code. The framework deliberately never
assigns meaning to it — *you* do. In a multi-platform project (C# + Java + Lua + Objective-C…)
that means the same command code must agree across every end, and hand-aligning N copies is a
classic source of "compiles fine, dispatches wrong" bugs.

This tool keeps **one source of truth** ([`commands.json`](commands.json)) and generates the
matching enum/constants for each platform, so you edit once and regenerate.

```
                        ┌──────────► generated/RelayCommand.cs    (C# enum)
commands.json ──gen.py──┤            generated/RelayCommand.java  (Java int constants)
 (single source)        ├──────────► generated/relay_command.lua  (Lua table)
                        └──────────► generated/RelayCommand.h     (Obj-C NS_ENUM)
```

> These are **placeholder** commands. Keep your real, business-named command list in your own
> private project — this repo only ships the scaffold + an example.

## Use

Requires Python 3.8+.

```bash
python gen.py            # (re)generate into ./generated/
python gen.py --check    # generate in memory; exit 1 if ./generated/ is missing or stale
```

Edit [`commands.json`](commands.json), rerun `gen.py`, copy/point each generated file into the
corresponding project. Add a language by adding one generator function + one entry to `TARGETS`
in [`gen.py`](gen.py).

## Disciplines this enforces (cross-end command codes are unforgiving)

1. **Explicit values, never auto-increment.** Inserting a command must not shift the others —
   ends won't re-renumber together. `gen.py` requires an explicit `value` per command.
2. **No duplicate names, no reused values.** `gen.py` fails loudly
   (`error: value 1 reused by 'B' and 'A'`) instead of silently generating a collision.
3. **Generated files are marked `AUTO-GENERATED — DO NOT EDIT`.** Change the JSON, not the output.
4. **CI guard against "edited JSON, forgot to regenerate".** Run `--check` in CI:

   ```yaml
   # e.g. GitHub Actions
   - run: python tools/codegen/gen.py --check
   ```

   It fails the build if anyone changed `commands.json` without committing the regenerated files.

> Retiring a command? Leave its value as a reserved placeholder — **never recycle a value** for a
> new meaning, or old and new builds will disagree on what that int means.

## License

MIT © 2026 Likeon
