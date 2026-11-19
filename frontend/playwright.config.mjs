import { defineConfig } from '@playwright/test'

/** 真实后端验收须显式启用，串行执行以避免共享权限配置互相干扰。 */
export default defineConfig({
  testDir: './e2e',
  workers: 1,
  timeout: 60000,
  retries: 0,
  outputDir: '../.local/ui-browser-results',
  reporter: [['list']],
  use: {
    baseURL: process.env.UI_BASE_URL || 'http://127.0.0.1:5183',
    viewport: { width: 1280, height: 800 },
    screenshot: 'only-on-failure',
  },
})
