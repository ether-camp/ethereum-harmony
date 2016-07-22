(function() {
    'use strict';

    var mainApp = angular.module('HarmonyApp', ['ngRoute']);

    mainApp.controller('AppCtrl', AppCtrl);

    /**
     * Routing area
     */
    mainApp.config(function($routeProvider, $locationProvider) {
        $routeProvider

            .when('/', {
                templateUrl : 'pages/home.html'
            })

            .when('/systemLog', {
                templateUrl : 'pages/systemLog.html',
                controller  : 'SystemLogCtrl'
            })

            .when('/rpcUsage', {
                templateUrl : 'pages/rpcUsage.html',
                controller  : 'RpcUsageCtrl'
            })

            .when('/peers', {
                templateUrl : 'pages/peers.html',
                controller  : 'PeersCtrl'
            });

        $locationProvider.html5Mode(true);
    });

    /**
     * App Controller
     */

    var isLogPageActive = false;        // TODO move to related controller
    var isPeersPageActive = false;      // TODO move to related controller
    var isRpcPageActive = false;        // TODO move to related controller

    var topicStorage = {};

    var connectionLostOnce = false;
    var stompClient = null;
    var simpleSuffixes = {
        suffixes: {
            B: "",
            KB: "K",
            MB: "M",
            GB: "G",
            TB: "T"
        }
    };

    function updateBlockCounter(value) {
        var blockCounter = $('#blockCounter');
        blockCounter.prop('Counter', blockCounter.attr('value')).stop().animate({
            Counter: '' + value
        }, {
            duration: 1500,
            easing: 'linear',
            step: function(now) {
                var value = Math.ceil(now);
                blockCounter.attr('value', value);
                blockCounter.text(numberWithCommas(value));
            }
        });
    }

    /**
     * @example 1000 -> "1,000"
     */
    function numberWithCommas(x) {
        return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    }

    function updateProgressBar(view, percentage) {
        $(view).css('width', percentage + "%");
        $(view).attr('aria-valuenow', percentage);
    }

    /**
     * Show top right bubble
     */
    function showToastr(topMessage, bottomMessage) {
        toastr.clear()
        toastr.options = {
            "positionClass": "toast-top-right",
            "closeButton": true,
            "progressBar": true,
            "showEasing": "swing",
            "timeOut": "4000"
        };
        toastr.warning('<strong>' + topMessage + '</strong> <br/><small>' + bottomMessage + '</small>');
    }

    AppCtrl.$inject = ['$scope', '$timeout', '$route', '$location', '$window'];

    function AppCtrl ($scope, $timeout, $route, $location, $window) {
        var vm = this;
        vm.isConnected = false;
        vm.data = {
            currentPage: "/",

            cpuUsage: 0,
            memoryOccupied: "",
            memoryFree: "",
            freeSpace: "",

            lastBlockNumber: 0,
            lastBlockTimeMoment: "loading...",
            lastBlockTransactions: "N/A",
            difficulty: "N/A",
            networkHashRate: "N/A",

            appVersion: "n/a",
            ethereumJVersion: "n/a"
        };

        var updateLogSubscription       = updateSubscriptionFun('/topic/systemLog', onSystemLogResult);
        var updatePeersSubscription     = updateSubscriptionFun('/topic/peers', onPeersListResult);
        var updateRpcSubscription       = updateSubscriptionFun('/topic/rpcUsage', onRpcUsageResult);

        /**
         * Listen for page changes and subscribe to 'systemLog' topic only when we stay on that page.
         * Unsubscribe otherwise.
         */
        $scope.$on('$routeChangeSuccess', function(event, data) {
            var path = data.$$route.originalPath;
            console.log('Page changed ' + path);
            vm.data.currentPage = path;

            // #1 Change subscription
            isLogPageActive = path == '/systemLog';
            isPeersPageActive = path == '/peers';
            isRpcPageActive = path == '/rpcUsage';
            var isMainPageActive = path == '/';
            updateLogSubscription(isLogPageActive);
            updatePeersSubscription(isPeersPageActive);
            updateRpcSubscription(isRpcPageActive);

            // #2 Change body scroll behavior depending on selected page
            $('body').css('overflow', isMainPageActive ? 'auto' : 'hidden');
        });

        /**
         * Listed for window resize and broadcast to all interested controllers.
         * In that way sub controllers should care of bind and unbind for this event manually
         */
        angular.element($window).bind('resize', function() {
            $scope.$broadcast('windowResizeEvent');
        });

        function setConnected(value) {
            if (!value) {
                // remove all saved subscriptions
                topicStorage = {};
            }
            vm.isConnected = value;
            console.log("Connected status " + value);
        }

        function connect() {
            console.log("Attempting to connect");

            var socket = new SockJS('/websocket');
            stompClient = Stomp.over(socket);
            // disable network data traces from Stomp library
            stompClient.debug = null;
            stompClient.connect(
                {},
                function(frame) {
                    setConnected(true);
                    if (connectionLostOnce) {
                        showToastr("Connection established", "");
                    }

                    console.log('Connected');

                    // subscribe for updates
                    stompClient.subscribe('/topic/initialInfo', onInitialInfoResult);
                    stompClient.subscribe('/topic/machineInfo', onMachineInfoResult);
                    stompClient.subscribe('/topic/blockchainInfo', onBlockchainInfoResult);
                    stompClient.subscribe('/topic/newBlockFrom', onNewBlockFromResult);
                    updateLogSubscription(isLogPageActive);
                    updatePeersSubscription(isPeersPageActive);
                    updateRpcSubscription(isRpcPageActive);

                    // get immediate result
                    stompClient.send('/app/machineInfo');
                    stompClient.send('/app/initialInfo');
                },
                function(error) {
                    disconnect();
                }
            );
        }

        function onPeersListResult(data) {
            var items = JSON.parse(data.body);

            $scope.$broadcast('peersListEvent', items);
        }

        function onRpcUsageResult(data) {
            var items = JSON.parse(data.body);

            $scope.$broadcast('rpcUsageListEvent', items);
        }

        function updateSubscriptionFun(topic, handler) {
            return function(doSubscribe) {
                if (vm.isConnected) {
                    var subscribed = topicStorage[topic] != null;
                    if (doSubscribe != subscribed ) {
                        if (doSubscribe) {
                            topicStorage[topic] = stompClient.subscribe(topic, handler);
                        } else {
                            topicStorage[topic].unsubscribe();
                            topicStorage[topic] = null;
                        }
                        console.log("Changed subscription to topic:" + topic + " " + doSubscribe);
                    }
                }
            };
        }

        function onSystemLogResult(data) {
            var msg = data.body;

            // send event to SystemLogCtrl
            $scope.$broadcast('systemLogEvent', msg);
        }

        function onMachineInfoResult(data) {
            var info = JSON.parse(data.body);

            $timeout(function() {
                vm.data.cpuUsage        = info.cpuUsage;
                vm.data.memoryOccupied  = filesize(info.memoryTotal - info.memoryFree);
                vm.data.memoryFree      = filesize(info.memoryFree);
                vm.data.freeSpace       = filesize(info.freeSpace);

                var memoryPercentage = 0;
                if (info.memoryTotal != 0) {
                    memoryPercentage = Math.round(100 * (info.memoryTotal - info.memoryFree) / info.memoryTotal);
                }

                updateProgressBar('#memoryUsageProgress', memoryPercentage);
                updateProgressBar('#cpuUsageProgress', info.cpuUsage);
            }, 10);
        }

        function onInitialInfoResult(data) {
            var info = JSON.parse(data.body);

            $timeout(function() {
                vm.data.appVersion = info.appVersion;
                vm.data.ethereumJVersion = info.ethereumJVersion;
            }, 10);

            console.log("App version " + info.appVersion);
            stompClient.unsubscribe('/topic/initialInfo');
        }

        function onBlockchainInfoResult(data) {
            var info = JSON.parse(data.body);

            $timeout(function() {
                updateBlockCounter(info.lastBlockNumber);
                vm.data.lastBlockNumber         = info.lastBlockNumber;
                vm.data.lastBlockTime           = info.lastBlockTime;
                vm.data.lastBlockTimeMoment     = moment(info.lastBlockTime * 1000).fromNow();
                vm.data.lastBlockTimeString     = moment(info.lastBlockTime * 1000).format('hh:mm:ss MMM DD YYYY');
                vm.data.lastBlockTransactions   = info.lastBlockTransactions;
                vm.data.difficulty              = filesize(info.difficulty, simpleSuffixes);
                vm.data.lastReforkTime          = info.lastReforkTime;
                vm.data.networkHashRate         = filesize(info.networkHashRate, simpleSuffixes) + "H/s";
            }, 10);
        }

        function onNewBlockFromResult(data) {
            var item = JSON.parse(data.body);

            $scope.$broadcast('newBlockFromEvent', item);
        }

        function disconnect() {
            connectionLostOnce = true;
            showToastr("Connection Lost", "Reconnecting...");

            if (stompClient != null) {
                stompClient.disconnect();
            }
            setConnected(false);
            console.log("Disconnected. Retry ...");
            setTimeout(connect, 5000);
        }

        connect();
    }
})();