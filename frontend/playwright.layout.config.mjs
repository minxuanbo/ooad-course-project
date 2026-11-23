import { defineConfig } from '@playwright/test'

/** 隔离浏览器使用虚构HTTP夹具检查布局与交互；真实MySQL契约由后端测试独立验证。 */
export default defineConfig({
  testDir: './e2e',
  testMatch: 'prototype-alignment.spec.mjs',
  workers: 1,
  timeout: 30000,
  retries: 0,
  outputDir: '../.local/ui-prototype-alignment-20261124/browser-results',
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:5186',
    channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome',
    viewport: { width: 1440, height: 1000 },
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --port 5186',
    url: 'http://127.0.0.1:5186',
    reuseExistingServer: true,
  },
})
