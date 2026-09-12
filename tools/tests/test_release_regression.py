"""Release regression coverage follows the extracted feature structure.

The 44 preserved release assertions are split across test_release_regression_1.py
through test_release_regression_4.py. Shared source bindings live in
release_contract_context.py so assertions target the moved implementations rather
than the former MainActivity monolith.
"""
