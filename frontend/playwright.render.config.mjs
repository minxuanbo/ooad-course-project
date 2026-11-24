import { defineConfig } from '@playwright/test'

/** 运行真实Vue/Router的可控HTTP渲染回归，不连接业务库或正式后端。 */
export default defineConfig({
  testDir: './render-tests',
  testMatch: 'login-render.spec.mjs',
  workers: 1,
  timeout: 20000,
  retries: 0,
  outputDir: '../.local/login-render-browser-results',
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:5196',
    channel: 'chrome',
    viewport: { width: 1280, height: 800 },
  },
  webServer: {
    command: 'npm run preview -- --host 127.0.0.1 --port 5196 --strictPort',
    url: 'http://127.0.0.1:5196/login',
    reuseExistingServer: false,
  },
})
