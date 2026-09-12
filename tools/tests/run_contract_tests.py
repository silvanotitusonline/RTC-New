import importlib.util
from pathlib import Path

root = Path(__file__).parent
fail = []
count = 0
for path in sorted(root.glob('test_*.py')):
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    for name in sorted(x for x in dir(module) if x.startswith('test_')):
        count += 1
        try:
            getattr(module, name)()
            print('PASS', name)
        except Exception as exc:
            print('FAIL', name, ':', exc)
            fail.append(f'{path.name}::{name}')
print(f'\n{count - len(fail)}/{count} regression contracts passed')
if fail:
    print('Failures:')
    for item in fail:
        print(' -', item)
raise SystemExit(1 if fail else 0)
