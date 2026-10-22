const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({ testDir: './tests', testMatch: '*.spec.cjs', workers: 1, timeout: 45000, reporter: 'list', use: { trace: 'off' } });
