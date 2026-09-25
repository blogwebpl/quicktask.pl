// Keep browser cookies, CSRF validation and DPoP on the frontend's origin.
config.devServer = config.devServer || {};
config.devServer.proxy = [{
    context: pathname =>
        (/^\/auth(?:\/|$)/.test(pathname) &&
            !/^\/auth\/(?:callback|error)(?:\/|$)/.test(pathname)) ||
        /^\/(?:users|inbox|items|files|sync)(?:\/|$)/.test(pathname),
    target: process.env.CLEARMIND_API_URL || 'http://localhost:3000',
    changeOrigin: false
}];
