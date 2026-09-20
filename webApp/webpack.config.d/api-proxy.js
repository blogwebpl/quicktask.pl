// Keep browser cookies, CSRF validation and DPoP on the frontend's origin.
config.devServer = config.devServer || {};
config.devServer.proxy = [{
    context: ['/auth', '/users', '/inbox', '/items', '/files', '/sync'],
    target: process.env.CLEARMIND_API_URL || 'http://localhost:3000',
    changeOrigin: false
}];
