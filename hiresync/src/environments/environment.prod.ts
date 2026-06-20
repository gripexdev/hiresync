export const environment = {
  production: true,
  // Relative paths: Nginx (see hiresync/nginx.conf) reverse-proxies /api and /ws
  // to the backend container, so the frontend always calls its own origin.
  apiUrl: '/api',
  wsUrl: '/ws',
  // Remember to add the production domain to "Authorized JavaScript origins"
  // on this OAuth client in Google Cloud Console before deploying.
  googleClientId: '270177959255-o04nvrs03nhrgejhog2tt4u6rvuvm1gl.apps.googleusercontent.com',
};
