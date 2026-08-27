import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * 프론트가 부르는 API 경로가 전부 개발 서버 프록시에 등록되어 있는지 본다.
 *
 * 규칙이 빠지면 요청이 백엔드에 닿지 못하고 개발 서버가 index.html을 200으로 돌려준다. 화면은 JSON 대신 HTML 문자열을 받아
 * `zones.map is not a function` 같은 엉뚱한 곳에서 터진다. 실제로 `/api/my-zones`가 이렇게 빠져 있었다.
 */
const projectRoot = resolve(__dirname, '../..');
const sourceRoot = join(projectRoot, 'src');
const thisFile = join(sourceRoot, 'api', 'proxy.test.ts');

function sourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return sourceFiles(path);
    if (!/\.tsx?$/.test(entry.name)) return [];
    if (path === thisFile) return [];
    return [path];
  });
}

function proxyPrefixes(): string[] {
  const config = readFileSync(join(projectRoot, 'vite.config.ts'), 'utf8');
  return [...config.matchAll(/'(\/api\/[^']+)':\s*\{/g)].map((match) => match[1]);
}

function requestedPaths(): string[] {
  const paths = new Set<string>();
  for (const file of sourceFiles(sourceRoot)) {
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(/['"`](\/api\/[a-z0-9-]+)/gi)) {
      paths.add(match[1]);
    }
  }
  return [...paths].sort();
}

describe('개발 서버 프록시', () => {
  it('프론트가 부르는 모든 API 경로에 프록시 규칙이 있다', () => {
    const prefixes = proxyPrefixes();
    expect(prefixes.length).toBeGreaterThan(0);

    const requested = requestedPaths();
    expect(requested.length).toBeGreaterThan(0);

    const missing = requested.filter((path) => !prefixes.some((prefix) => path.startsWith(prefix)));

    expect(missing, `vite.config.ts 프록시에 빠진 경로: ${missing.join(', ')}`).toEqual([]);
  });
});
