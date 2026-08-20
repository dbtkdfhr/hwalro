import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 구체적인 경로를 범용 /api보다 먼저 선언한다.
    // 개발 서버에서는 서비스별 프록시를 통해 브라우저의 교차 출처 요청을 피한다.
    proxy: {
      '/api/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api/drawings': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/simulations': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      // 탐색을 시작하고 조회하는 경로는 /api/simulations 아래지만, 진행 중인 탐색을 다루는 경로는
      // /api/layout-searches 아래다. 이 규칙이 없으면 취소·단건 조회·시뮬레이션 준비 요청이 백엔드에
      // 닿지 못하고 개발 서버의 404를 받는다.
      '/api/layout-searches': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/regulations': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api/reports': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api/risks': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api/safety-checks': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
});
