(function() {
    'use strict';

    var jsonrpcModule = angular.module('angular-jsonrpc-client', []);

    jsonrpcModule.service('jsonrpc', jsonrpc);
    jsonrpcModule.provider('jsonrpcConfig', jsonrpcConfig);

    jsonrpc.$inject = ['$q', '$http', 'jsonrpcConfig'];

    var id = 0;
    var ERROR_TYPE_SERVER = 'JsonRpcServerError';
    var ERROR_TYPE_TRANSPORT = 'JsonRpcTransportError';
    var ERROR_TYPE_CONFIG = 'JsonRpcConfigError';
    var DEFAULT_SERVER_NAME = 'main';
    var DEFAULT_HEADERS = {
        'Content-Type': 'application/json',
    };

    function JsonRpcTransportError(error) {
        this.name = ERROR_TYPE_TRANSPORT;
        this.message = error;
    }
    JsonRpcTransportError.prototype = Error.prototype;

    function JsonRpcServerError(error) {
        this.name    = ERROR_TYPE_SERVER;
        this.message = error.message;
        this.error   = error;
        this.data    = error.data;
    }
    JsonRpcServerError.prototype = Error.prototype;

    function JsonRpcConfigError(error) {
        this.name = ERROR_TYPE_CONFIG;
        this.message = error;
    }
    JsonRpcConfigError.prototype = Error.prototype;

    function jsonrpc($q, $http, jsonrpcConfig) {
        var extraHeaders = {};

        return {
            request              : request,
            setHeaders           : setHeaders,
            batch                : batch,
            ERROR_TYPE_SERVER    : ERROR_TYPE_SERVER,
            ERROR_TYPE_TRANSPORT : ERROR_TYPE_TRANSPORT,
            ERROR_TYPE_CONFIG    : ERROR_TYPE_CONFIG,
            JsonRpcTransportError: JsonRpcTransportError,
            JsonRpcServerError   : JsonRpcServerError,
            JsonRpcConfigError   : JsonRpcConfigError
        };

        function _getInputData(methodName, args) {
            id += 1;
            return {
                jsonrpc: '2.0',
                id     : id,
                method : methodName,
                params : args
            }
        }

        function _findServer(serverName) {
            if (jsonrpcConfig.servers.length === 0) {
                throw new JsonRpcConfigError('Please configure the jsonrpc client first.');
            }

            var servers = jsonrpcConfig.servers.filter(function(s) { return s.name === serverName; });

            if (servers.length === 0) {
                throw new JsonRpcConfigError('Server "' + serverName + '" has not been configured.');
            }

            return servers[0];
        }

        function _determineArguments(args) {
            if (typeof(args[0]) === 'object') {
                return args[0];
            }
            else if (args.length === 2) {
                return {
                    serverName: DEFAULT_SERVER_NAME,
                    methodName: args[0],
                    methodArgs: args[1],
                };
            }
            else {
                return {
                    serverName: args[0],
                    methodName: args[1],
                    methodArgs: args[2],
                };
            }
        }

        function _determineHeaders(serverName) {
            var extra = extraHeaders[serverName] ? extraHeaders[serverName] : {};
            var server = _findServer(serverName);
            var headers = angular.extend(server.headers, extra);
            return angular.extend(headers, DEFAULT_HEADERS);
        }

        function _determineErrorDetails(data, status, url) {
            // 2. Call was received by the server. Server returned an error.
            // 3. Call did not arrive at the server.
            var errorType = ERROR_TYPE_TRANSPORT;
            var errorMessage;

            if (status === 0) {
                // Situation 3
                errorMessage = 'Connection refused at ' + url;
            }
            else if (status === 404) {
                // Situation 3
                errorMessage = '404 not found at ' + url;
            }
            else if (status === 500) {
                // This could be either 2 or 3. We have to look at the returned data
                // to determine which one.
                if (data.jsonrpc && data.jsonrpc === '2.0') {
                    // Situation 2
                    errorType = ERROR_TYPE_SERVER;
                    errorMessage = data.error;
                }
                else {
                    // Situation 3
                    errorMessage = '500 internal server error at ' + url + ': ' + data;
                }
            }
            else if (status === -1) {
                errorMessage = 'Timeout or cancelled';
            }
            else {
                // Situation 3
                errorMessage = 'Unknown error. HTTP status: ' + status + ', data: ' + data;
            }

            return {
                type   : errorType,
                message: errorMessage,
            };
        }

        function setHeaders(serverName, headers) {
            var server = _findServer(serverName);

            extraHeaders[server.name] = headers;
        }

        function request(arg1, arg2, arg3) {
            var args = _determineArguments(arguments);

            var deferred = $q.defer();

            var server;

            try {
                server = _findServer(args.serverName);
            }
            catch(err) {
                deferred.reject(err);
                return deferred.promise;
            }

            var inputData = _getInputData(args.methodName, args.methodArgs);
            var headers = _determineHeaders(args.serverName);

            var req = {
                method : 'POST',
                url    : server.url,
                headers: headers,
                data   : inputData
            };


            if (args.config) {
                Object.keys(args.config).forEach(function(key) {
                    req[key] = args.config[key];
                })
            }

            var promise = $http(req);

            if (jsonrpcConfig.returnHttpPromise) {
                return promise;
            }

            // Here, we determine which situation we are in:
            // 1. Call was a success.
            // 2. Call was received by the server. Server returned an error.
            // 3. Call did not arrive at the server.
            //
            // 2 is a JsonRpcServerError, 3 is a JsonRpcTransportError.
            //
            // We are assuming that the server can use either 200 or 500 as
            // http return code in situation 2. That depends on the server
            // implementation and is not determined by the JSON-RPC spec.
            promise.success(function(data, status, headers, config) {
                    // In some cases, it is unfortunately possible to end up in
                    // promise.success with data being undefined.
                    // This is likely caused either by a bug in the $http service
                    // or by incorrect usage of $http interceptors.
                    if (!data) {
                        return deferred.reject(
                            'Unknown error, possibly caused by incorrectly configured $http interceptor. ' +
                            'See https://github.com/joostvunderink/angular-jsonrpc-client/issues/16 for ' +
                            'more information.');
                    }

                    if (data.result !== undefined) {
                        // Situation 1
                        deferred.resolve(data.result);
                    }
                    else {
                        // Situation 2
                        deferred.reject(new JsonRpcServerError(data.error));
                    }
                })
                .error(function(data, status, headers, config) {
                    // Situation 2 or 3.
                    var errorDetails = _determineErrorDetails(data, status, server.url);

                    if (errorDetails.type === ERROR_TYPE_TRANSPORT) {
                        deferred.reject(new JsonRpcTransportError(errorDetails.message));
                    }
                    else {
                        deferred.reject(new JsonRpcServerError(errorDetails.message));
                    }
                });

            return deferred.promise;
        }

        function batch(server) {
            var _server = server == null ? DEFAULT_SERVER_NAME : server;
            var _data = [];

            this.add = function(methodName, args) {
                var data = _getInputData(methodName, args);
                var req = {
                    deferred: $q.defer(),
                    data: data,
                    id: data.id
                };
                _data.push(req);

                if (jsonrpcConfig.returnHttpPromise) {
                    return data.id;
                }

                return req.deferred.promise;
            };

            this.send = function () {
                var deferred = $q.defer();

                var server;

                try {
                    server = _findServer(_server);
                }
                catch(err) {
                    deferred.reject(err);
                    return deferred.promise;
                }

                var headers = _determineHeaders(_server);

                var req = {
                    method: 'POST',
                    url: server.url,
                    headers: headers,
                    data: _getRequestData()
                };

                var promise = $http(req);

                if (jsonrpcConfig.returnHttpPromise) {
                    _data = [];
                    return promise;
                }

                promise.success(function (data, status, headers, config) {
                        data.forEach(function(d) {
                            var deferred = _getDeferred(d.id);

                            if (d.result !== undefined) {
                                // Situation 1
                                deferred.resolve(d.result);
                            }
                            else {
                                // Situation 2
                                deferred.reject(new JsonRpcServerError(d.error));
                            }
                        });
                    })
                    .error(function (data, status, headers, config) {
                        data.forEach(function(d) {
                            var deferred = _getDeferred(d.id);

                            // Situation 2 or 3.
                            var errorDetails = _determineErrorDetails(d, status, server.url);

                            if (errorDetails.type === ERROR_TYPE_TRANSPORT) {
                                deferred.reject(new JsonRpcTransportError(errorDetails.message));
                            }
                            else {
                                deferred.reject(new JsonRpcServerError(errorDetails.message));
                            }
                        });
                    });

                return $q.all(_getAllPromises())
                    .then(function () {
                        _data = [];
                    });
            };

            function _getRequestData() {
                return _data.map(function(d) {
                    return d.data;
                });
            }

            function _getDeferred(id) {
                var found = _data.filter(function(d) {
                    return d.id === id;
                });
                if (found.length === 1) {
                    return found[0].deferred;
                }
            }

            function _getAllPromises() {
                return _data.map(function(d) {
                    return d.deferred.promise;
                });
            }

            return this;
        }
    }

    function jsonrpcConfig() {
        var config = {
            servers: [],
            returnHttpPromise: false
        };

        this.set = function(args) {
            if (typeof(args) !== 'object') {
                throw new Error('Argument of "set" must be an object.');
            }

            var allowedKeys = ['url', 'servers', 'returnHttpPromise'];
            var keys = Object.keys(args);
            keys.forEach(function(key) {
                if (allowedKeys.indexOf(key) < 0) {
                    throw new JsonRpcConfigError('Invalid configuration key "' + key + '". Allowed keys are: ' +
                        allowedKeys.join(', '));
                }

                if (key === 'url') {
                    config.servers = [{
                        name: DEFAULT_SERVER_NAME,
                        url: args[key],
                        headers: {}
                    }];
                }
                else if (key === 'servers') {
                    config.servers = getServers(args[key]);
                }
                else {
                    config[key] = args[key];
                }
            });
        };

        function getServers(data) {
            if (!(data instanceof Array)) {
                throw new JsonRpcConfigError('Argument "servers" must be an array.');
            }
            var servers = [];

            data.forEach(function(d) {
                if (!d.name) {
                    throw new JsonRpcConfigError('Item in "servers" argument must contain "name" field.');
                }
                if (!d.url) {
                    throw new JsonRpcConfigError('Item in "servers" argument must contain "url" field.');
                }
                var server = {
                    name: d.name,
                    url: d.url,
                };
                if (d.hasOwnProperty('headers')) {
                    server.headers = d.headers;
                }
                else {
                    server.headers = {};
                }
                servers.push(server);
            });

            return servers;
        }

        this.$get = function() {
            return config;
        };
    }
}).call(this);
