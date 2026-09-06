const { defineConfig } = require('@playwright/test')

const buildId = process.env.RTC_APPETIZE_BUILD_ID

if (!buildId) {
  throw new Error(
    'RTC_APPETIZE_BUILD_ID is required. Supply only the build ID for a newly uploaded, verified non-production debug APK.',
  )
}

module.exports = defineConfig({
  testDir: './tests',
  timeout: 45_000,
  expect: { timeout: 15_000 },
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    config: {
      publicKey: buildId,
      device: 'pixel7',
      osVersion: '13.0',
      debug: true,
      grantPermissions: false,
      userInteractionDisabled: true,
    },
  },
  projects: [{ name: 'pixel7-resident' }],
})
