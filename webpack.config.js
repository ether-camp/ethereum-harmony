var path = require('path'),
    appPath = path.join(__dirname, 'src/main/resources/static/'),
    wwwPath = path.join(__dirname, 'src/main/resources/static/js/bin'),
    webpack = require("webpack");

var precss       = require('precss');
var autoprefixer = require('autoprefixer');

var HtmlWebpackPlugin = require('html-webpack-plugin');
var WebpackCleanupPlugin = require('webpack-cleanup-plugin');

var config = {
    entry:
        {
          app: path.join(appPath, 'js/index.js'),
          vendor: [
              'jquery',
              //'bootstrap-webpack',
              'angular',
              'angular-animate',
              'angular-ui-router',
            ]
        },
    output: {
        path: wwwPath,
        filename: 'app.bundle.js'
    },
    module: {
        loaders: [
            {
            test: /\.html$/,
            loader: 'html'
        },
            {
            test: /\.json$/,
            loader: "json"
        },
          {
            test: /\.(png|jpg)$/,
            loader: 'file?name=img/[name].[ext]'
         },
            {
            test: /\.css$/,
            loader: "style!css"
        },
            {
            test: /\.js$/,
            exclude: /(node_modules|lib)/,
            loader: "babel"
        },
            {
            test: /\.scss$/,
            loader: "style!css!postcss!sass"
        },
            {
            test: [/\.svg/, /\.eot/, /\.ttf/, /\.woff/],
            loader: 'file?name=fonts/[name].[ext]'
        },
        // the url-loader uses DataUrls.
        // the file-loader emits files.
        { test: /\.(woff|woff2)$/,  loader: "url-loader?limit=10000&mimetype=application/font-woff" },
        { test: /\.ttf$/,    loader: "file-loader" },
        { test: /\.eot$/,    loader: "file-loader" },
        { test: /\.svg$/,    loader: "file-loader" }
        ]
    },
    postcss: function () {
        return [precss, autoprefixer];
    },
    resolve: {
        extensions: ['', '.js', '.json', '.scss', '.html'],
        root: [
            appPath,
            path.join(__dirname, 'node_modules'),
            path.join(__dirname, 'src/main/resources/static/lib')
        ],
        moduleDirectories: [
            'node_modules',
            'lib'
        ]
    },
    externals: {
        // require("jquery") is external and available
        //  on the global var jQuery
        "bootstrap": "bootstrap"
    },

    plugins: []
};

var isProd = process.env.NODE_ENV == 'production';

if (!isProd) {
    config.devtool = 'source-map';
}

//plugins;
var plugins = config.plugins;

if (isProd) {
    plugins.push(new WebpackCleanupPlugin({
        exclude: ['.gitignore']
    }));
}

plugins.push(new webpack.optimize.CommonsChunkPlugin(/* chunkName= */"vendor", /* filename= */"vendor.bundle.js"));
//plugins.push(new HtmlWebpackPlugin({
//    filename: 'index.html',
//    template: path.join(appPath, 'index.html')
//}));

isProd && plugins.push(new webpack.optimize.UglifyJsPlugin());

module.exports = config;
