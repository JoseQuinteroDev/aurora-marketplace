// Dev proxy for the Angular storefront.
//
// Default: talk straight to the core backend (simplest local workflow).
// Set AURORA_API_TARGET=http://localhost:8088 to route the storefront through
// the API gateway instead — the same single entry point used in production.
const target = process.env.AURORA_API_TARGET || 'http://localhost:8080';

module.exports = {
  '/api': {
    target,
    secure: false,
    changeOrigin: true
  },
  '/actuator': {
    target,
    secure: false,
    changeOrigin: true
  }
};
