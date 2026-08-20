import { existsSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const UV_VERSION = '0.11.32';
const rootDirectory = join(dirname(fileURLToPath(import.meta.url)), '..');
const engineDirectory = join(rootDirectory, 'apps', 'simulation-service', 'engine');
const toolsDirectory = join(engineDirectory, '.tools', 'uv');
const virtualEnvironment = join(engineDirectory, '.venv');
const isWindows = process.platform === 'win32';
const uv = join(toolsDirectory, isWindows ? 'uv.exe' : 'uv');
const python = isWindows
  ? join(virtualEnvironment, 'Scripts', 'python.exe')
  : join(virtualEnvironment, 'bin', 'python');

function run(command, args, options = {}) {
  console.log(`> ${command} ${args.join(' ')}`);
  const result = spawnSync(command, args, {
    cwd: engineDirectory,
    env: { ...process.env, UV_MANAGED_PYTHON: '1', ...options.env },
    stdio: 'inherit',
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`${options.description ?? command} failed (exit code: ${result.status})`);
  }
}

function assertSupportedPlatform() {
  const supported =
    (process.platform === 'win32' && process.arch === 'x64') ||
    (process.platform === 'darwin' && ['arm64', 'x64'].includes(process.arch));
  if (!supported) {
    throw new Error(
      `Unsupported engine platform: ${process.platform}/${process.arch}. ` +
        'Supported platforms are Windows x64, macOS Apple Silicon, and macOS Intel.',
    );
  }
}

function installUv() {
  if (existsSync(uv)) return;
  mkdirSync(toolsDirectory, { recursive: true });
  const installer = `https://astral.sh/uv/${UV_VERSION}/install.${isWindows ? 'ps1' : 'sh'}`;
  const env = {
    UV_UNMANAGED_INSTALL: toolsDirectory,
    UV_NO_MODIFY_PATH: '1',
  };
  if (isWindows) {
    run(
      'powershell.exe',
      ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', `irm '${installer}' | iex`],
      { description: 'uv installation', env },
    );
  } else {
    run('/bin/sh', ['-c', `curl --proto '=https' --tlsv1.2 -LsSf '${installer}' | sh`], {
      description: 'uv installation',
      env,
    });
  }
}

assertSupportedPlatform();
installUv();
run(uv, ['--version'], { description: 'uv verification' });
run(uv, ['python', 'install', '3.12'], { description: 'Python 3.12 installation' });
run(uv, ['venv', '--managed-python', '--clear', '--python', '3.12', virtualEnvironment], {
  description: 'virtual environment creation',
});
run(
  uv,
  [
    'pip',
    'install',
    '--python',
    python,
    '--no-deps',
    '--requirement',
    join(engineDirectory, 'requirements.txt'),
  ],
  { description: 'runtime dependency installation' },
);
run(
  python,
  [
    '-c',
    [
      'import importlib.metadata, struct, sys',
      'import jupedsim',
      'assert sys.version_info[:2] == (3, 12)',
      "assert struct.calcsize('P') == 8",
      "assert importlib.metadata.version('jupedsim') == '1.4.2'",
      'assert jupedsim.Agent.position.fset is not None',
    ].join('; '),
  ],
  { description: 'custom JuPedSim verification' },
);
run(python, [join(engineDirectory, 'runner.py'), '--version'], {
  description: 'runner verification',
});

console.log(`Hwalro simulation engine is ready: ${python}`);
