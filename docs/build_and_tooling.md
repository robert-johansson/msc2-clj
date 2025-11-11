# Build & Tooling Notes

## Building MSC2

```bash
cd external/msc2
./build.sh        # builds ./NAR
./NAR shell       # interactive REPL
```

`build.sh` compiles all `src/*.c` and `src/NetworkNAR/*.c` into the `NAR` binary. On Apple Silicon we drop `-mfpmath=sse -msse2` (already patched in our fork). `libbuild.sh` can produce static/shared libraries and install headers under `/usr/local/include/ona/`.

## Useful Shell Commands

- `./NAR shell` – run the interactive shell; enter Narsese, use `quit` to exit.
- `./NAR` – run built-in regression tests (C-side).
- `*reset`, `*stats`, `*concepts`, `*babblingops=`, `*motorbabbling=` – inspect/tune the runtime.
- `python3 experiments/*.py` – run scripted experiments (ensure `pexpect` is installed or use the repo venv).

## Dependencies

- Standard C toolchain (GCC/Clang). No external libs except `pthread`.
- Python 3 + `pexpect` for experiments (`python3 -m pip install --user pexpect` or use the root `venv/`).

## Workflow Tips

1. **Keep `external/msc2` clean:** remove `NAR`, `.dSYM`, and `__pycache__` before committing.
2. **Use the root venv:** `python3 -m venv venv && source venv/bin/activate` to isolate Python deps.
3. **Record experiments:** Each script generates CSV logs—commit them when they serve as golden data.
4. **Submodule updates:** After pushing MSC2 changes to your fork, run `git add external/msc2` in the root repo and commit the pointer bump.

## Common Issues

- **Missing SSE flags on arm64:** Already handled in our fork’s `build.sh`. If upstream pulls reintroduce the flags, reapply the arch guard.
- **Stale NAR:** If experiments fail with `FileNotFoundError: '../NAR'`, rebuild via `./build.sh`.
- **Conflicting stamps/concepts:** Use `*concepts` to inspect truth values; use `*stats` to verify attention queues.

Following these guidelines keeps the C reference reproducible and aligned with the Clojure porting effort.***
