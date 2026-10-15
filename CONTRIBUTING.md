# Contributing

Start with a reproducible failure and observable evidence, rather than a new dashboard feature.

For changes to the existing scenario:

1. Use JDK 21+ and Python 3.10+.
2. Run `python3 tests/verify.py`.
3. Describe the trigger and the measured before/after behavior.
4. Keep execution bounded; test cleanup and error paths.
5. Keep generated `artifacts/` and private production recordings out of commits.

Before implementing a new scenario, open an issue describing its trigger, expected evidence, repair, limits, and verification contract. Prefer synthetic fixtures that can be safely run on a developer laptop. Each scenario must report unsupported or unexpected observations honestly.

No external service or AI model should be required for the core reproduction. English documentation keeps the project accessible; Korean translations are welcome.
