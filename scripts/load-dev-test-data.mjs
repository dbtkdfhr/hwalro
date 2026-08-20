import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, '..');
const fixtures = [
  { database: 'hwalro_auth', path: 'apps/auth-service/src/main/resources/db/dml.sql' },
  {
    database: 'hwalro_regulation',
    path: 'apps/regulation-service/src/main/resources/db/safety-check-dml.sql',
  },
  {
    database: null,
    path: 'apps/simulation-service/src/main/resources/db/density-threshold-dml.sql',
  },
];

const fixtureSql = (
  await Promise.all(
    fixtures.map(async ({ database, path }) => {
      const sql = await readFile(resolve(repositoryRoot, path), 'utf8');
      const useDatabase = database ? `USE ${database};\n` : '';
      return `-- ${path}\n${useDatabase}${sql}`;
    }),
  )
).join('\n\n');

await new Promise((resolvePromise, rejectPromise) => {
  const mysql = spawn(
    'docker',
    [
      'compose',
      'exec',
      '-T',
      'mysql',
      'sh',
      '-c',
      'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD"',
    ],
    {
      cwd: repositoryRoot,
      stdio: ['pipe', 'inherit', 'inherit'],
    },
  );

  mysql.stdin.end(fixtureSql);

  mysql.once('error', (error) => {
    rejectPromise(new Error('Failed to start the MySQL fixture loader.', { cause: error }));
  });

  mysql.once('exit', (code) => {
    if (code !== 0) {
      rejectPromise(new Error(`Failed to load MySQL development fixtures. exitCode=${code}`));
      return;
    }
    resolvePromise();
  });
});

console.log(`Loaded development fixtures: ${fixtures.map(({ path }) => path).join(', ')}`);
