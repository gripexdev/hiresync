// Karma configuration — see https://karma-runner.github.io/6.4/config/configuration-file.html
// CHROME_BIN must point to a real Chrome/Chromium binary — set it in the
// environment (CI workflow, Docker run command) rather than hardcoding a path
// here, since it differs between local machines and CI runners.
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {},
      clearContext: false, // leave Jasmine Spec Runner output visible in the browser
    },
    jasmineHtmlReporter: {
      suppressAll: true, // remove duplicated traces
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/hiresync'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }],
    },
    reporters: ['progress', 'kjhtml'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    browsers: ['Chrome'],
    customLaunchers: {
      // Headless + --no-sandbox: required when Chrome runs as root, which is
      // the case both inside our Docker-based local test runs and on most
      // GitHub Actions runners.
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
      },
    },
    singleRun: false,
    restartOnFileChange: true,
  });
};
