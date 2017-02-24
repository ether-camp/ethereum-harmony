(function() {
    'use strict';

    // Angular modules definition
    var mainApp = angular.module('HarmonyApp', [
        'ui.bootstrap.showErrors',      // form validation extension
        'ngRoute',                      // sub page navigation
        'angular-jsonrpc-client',       // json-rpc communication
        'ngStomp',                      // websocket communication
        'angularModalService',          // for showing modal popups
        'scope-util'
    ]);

    mainApp.controller('AppCtrl', AppCtrl);

    mainApp.constant('scrollConfig', {
        autoHideScrollbar: true,
        theme: 'dark',
        advanced: {
            updateOnContentResize: true
        },
        axis: 'y',
        setHeight: 200,
        scrollInertia: 0,
        // having this will cause container to scroll down whenever Cmd or Ctrl pressed
        //keyboard: { enable: false },
        scrollButtons: { enable: false }
    });

    var JSON_RPC_URL = '/rpc';


    /**
     * Routing area
     */
    mainApp.config(function($routeProvider, $locationProvider, jsonrpcConfigProvider) {
        $routeProvider

            .when('/', {
                templateUrl : 'pages/home.html',
                controller  : 'HomeCtrl'
            })

            .when('/systemLog', {
                templateUrl : 'pages/systemLog.html',
                controller  : 'SystemLogCtrl'
            })

            .when('/rpcUsage', {
                templateUrl : 'pages/rpcUsage.html',
                controller  : 'RpcUsageCtrl'
            })

            .when('/terminal', {
                templateUrl : 'pages/terminal.html',
                controller  : 'TerminalCtrl'
            })

            .when('/peers', {
                templateUrl : 'pages/peers.html',
                controller  : 'PeersCtrl'
            })

            .when('/wallet', {
                templateUrl : 'pages/wallet.html',
                controller  : 'WalletCtrl'
            })

            .when('/contracts', {
                templateUrl : 'pages/contracts.html',
                controller  : 'ContractsCtrl'
            })

            .when('/contractNew', {
                templateUrl : 'pages/contract.new.html',
                controller  : 'ContractNewCtrl'
            });

        $locationProvider.html5Mode(true);

        jsonrpcConfigProvider.set({
            url: JSON_RPC_URL,
            returnHttpPromise: false
        });
    });

    /**
     * App Controller
     */

    /**
     * @type {Object.<string, Object>}
     */
    var topicStorage = {};

    var connectionLostOnce = false;
    var simpleSuffixes = {
        suffixes: {
            B: ' ',
            KB: 'K',
            MB: 'M',
            GB: 'G',
            TB: 'T'
        }
    };

    /**
     * Remember confirmed transactions to avoid twice notifications.
     * @type {String[]}
     */
    var confirmedTransactions = [];

    // change default animation step time to improve performance
    jQuery.fx.interval = 100;


    function formatBigDigital(value, decimals) {
        if(value == 0) return '0 ';
        var k = 1000;
        var dm = decimals + 1 || 3;
        var sizes = ['', 'K', 'M', 'G', 'T', 'P', 'E', 'Z', 'Y'];
        var i = Math.floor(Math.log(value) / Math.log(k));
        return parseFloat((value / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    }


    function updateBlockCounter(value) {
        var blockCounter = $('#blockCounter');
        if (value == blockCounter.attr('value')) {
            return;
        }

        blockCounter.stop(true, false).prop('Counter', blockCounter.attr('value')).animate({
            Counter: '' + value
        }, {
            duration: 1500,
            easing: 'linear',
            step: function(now) {
                //console.log('Interval step ' + c++);
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
            "timeOut": "4000"
        };
        toastr.warning('<strong>' + topMessage + '</strong> <br/><small>' + bottomMessage + '</small>');
    }

    /**
     * Show success top right bubble
     */
    function showSuccessToastr(topMessage, bottomMessage) {
        toastr.options = {
            "positionClass": "toast-top-right",
            "closeButton": true,
            "timeOut": "4000"
        };
        toastr.success('<strong>' + topMessage + '</strong> <br/><small>' + bottomMessage + '</small>');
    }

    AppCtrl.$inject = ['$scope', 'scopeUtil', '$window', '$stomp'];

    function AppCtrl ($scope, scopeUtil, $window, $stomp) {
        var vm = this;
        vm.isConnected = false;
        vm.data = {
            currentPage: "/",

            cpuUsage: 0,
            memoryOccupied: '',
            memoryFree: '',
            freeSpace: '',

            highestBlockNumber: 0,
            lastBlockNumber: 0,
            lastBlockTimeMoment: "loading...",
            lastBlockTransactions: "N/A",
            difficulty:         'N/A',
            networkHashRate:    'N/A',

            appVersion:         'n/a',
            ethereumJVersion:   'n/a',
            networkName:        'n/a',
            explorerUrl:        '',
            genesisHash:        'n/a',
            serverStartTime:    'n/a',
            nodeId:             'n/a',
            rpcPort:            'n/a',
            isPrivateNetwork:   false,

            publicIp:           ' ',
            publicIpLabel:      'IP'
        };

        $scope.isLoadingState = true;
        $scope.isSyncComplete = false;
        $scope.isSyncOff = false;
        $scope.isLoadingStateWithBlocks = false;
        $scope.loadingStateSpeed = 0;
        $scope.loadingItemsProgress = 0;
        $scope.syncStatus = {curCnt: 0, knownCnt: 0};
        $scope.oldStateNodesCount = 0;
        $scope.lastStateUpdateTime = 0;
        $scope.syncStateReceived = false;
        //$scope.isLoadingComplete = false;   // can we show block chart or not

        function jsonParseAndBroadcast(event) {
            return function(data) {
                $scope.$broadcast(event, (data));
            }
        }

        var updateLogSubscription       = updateSubscriptionFun('/topic/systemLog', jsonParseAndBroadcast('systemLogEvent'),
            function() {
                $stomp.send('/app/currentSystemLogs');
            });
        var updatePeersSubscription     = updateSubscriptionFun('/topic/peers', jsonParseAndBroadcast('peersListEvent'));
        var updateRpcSubscription       = updateSubscriptionFun('/topic/rpcUsage', jsonParseAndBroadcast('rpcUsageListEvent'));
        var updateBlockSubscription     = updateSubscriptionFun('/topic/newBlockInfo', jsonParseAndBroadcast('newBlockInfoEvent'),
            function() {
                $stomp.send('/app/currentBlocks');
            });
        var updateNetworkSubscription    = updateSubscriptionFun('/topic/networkInfo', jsonParseAndBroadcast('networkInfoEvent'));
        var updateWalletSubscription     = updateSubscriptionFun('/topic/getWalletInfo', jsonParseAndBroadcast('walletInfoEvent'),
            function() {
                $stomp.send('/app/getWalletInfo');
            });

        /**
         * Listen for page changes and subscribe to page relevant topic only when we stay on that page.
         * Unsubscribe otherwise.
         */
        $scope.$on('$routeChangeSuccess', function(event, data) {
            var path = data.$$route.originalPath;
            console.log('Page changed ' + path);
            vm.data.currentPage = path;

            // #1 Change subscription
            var isLongPageActive = path == '/???';
            updatePageSubscriptions();

            // #2 Change body scroll behavior depending on selected page
            $('body').css('overflow', isLongPageActive ? 'auto' : 'hidden');
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
            //console.log("Connected status " + value);
            $scope.$broadcast('connectedEvent');
        }

        function updatePageSubscriptions() {
            var path = vm.data.currentPage;
            updateNetworkSubscription(path == '/');
            updateBlockSubscription(path == '/');
            updateLogSubscription(path == '/systemLog');
            updatePeersSubscription(path == '/peers');
            updateRpcSubscription(path == '/rpcUsage');
            updateWalletSubscription(path == '/wallet');
            //updateContractsSubscription(path == '/contracts');
        }

        function connect() {
            //console.log("Attempting to connect");
            $stomp.setDebug(function (args) {
                //console.log(args);
            });

            $stomp.connect('/websocket', {}, disconnect)
                .then(function (frame) {
                    setConnected(true);
                    if (connectionLostOnce) {
                        showToastr('Connection established', '');
                    }

                    //console.log('Connected');

                    // subscribe for updates
                    $stomp.subscribe('/topic/initialInfo', onInitialInfoResult);
                    $stomp.subscribe('/topic/machineInfo', onMachineInfoResult);
                    $stomp.subscribe('/topic/blockchainInfo', onBlockchainInfoResult);
                    $stomp.subscribe('/topic/newBlockFrom', jsonParseAndBroadcast('newBlockFromEvent'));
                    $stomp.subscribe('/topic/currentSystemLogs', jsonParseAndBroadcast('currentSystemLogs'));
                    $stomp.subscribe('/topic/currentBlocks', jsonParseAndBroadcast('currentBlocksEvent'));
                    $stomp.subscribe('/topic/confirmTransaction', onConfirmedTransaction);

                    updatePageSubscriptions();

                    // get immediate result
                    $stomp.send('/app/machineInfo');
                    $stomp.send('/app/initialInfo');
                },
                function(error) {
                    // failed connect handler
                    // not called when connection dropped at some point of time
                    // prefer another disconnect handler
                }
            );
        }

        /**
         * Generate function to manage websocket topic subscription state.
         *
         * @param topic - topic to subscribe to
         * @param handler - handler for subscribed topic
         * @param initFun - optional parameter of function to be called before subscription
         * @returns {Function} - which accepts {doSubscribe} argument and manage subscription
         *                       depending on connection established or not.
         *                       {topicStorage} variable is used to keep connection state per topic
         */
        function updateSubscriptionFun(topic, handler, initFun) {
            return function(doSubscribe) {
                if (vm.isConnected) {
                    var subscribed = topicStorage[topic] != null;
                    if (doSubscribe != subscribed ) {
                        if (doSubscribe) {
                            initFun && initFun();
                            topicStorage[topic] = $stomp.subscribe(topic, handler);
                        } else {
                            topicStorage[topic].unsubscribe();
                            topicStorage[topic] = null;
                        }
                        console.log('Changed subscription to topic:' + topic + ' ' + doSubscribe);
                    }
                }
            };
        }

        function onMachineInfoResult(data) {
            var info = (data);

            scopeUtil.safeApply(function() {
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
            });
        }

        function onInitialInfoResult(data) {
            var info = (data);

            scopeUtil.safeApply(function() {
                vm.data.appVersion = info.appVersion;
                vm.data.ethereumJVersion = info.ethereumJVersion;
                vm.data.ethereumJBuildInfo = info.ethereumJBuildInfo;

                vm.data.networkName = info.networkName;
                $scope.explorerUrl = info.explorerUrl;
                vm.data.privateNetwork = info.privateNetwork;
                vm.data.genesisHash = info.genesisHash ? '0x' + info.genesisHash.substr(0, 6) : 'n/a';
                vm.data.serverStartTime = moment(info.serverStartTime).format('DD-MMM-YYYY, HH:mm');
                vm.data.nodeId = info.nodeId ? '0x' + info.nodeId.substr(0, 6) : 'n/a';
                vm.data.rpcPort = info.rpcPort;
                vm.data.publicIp = info.publicIp;
                vm.data.portCheckerUrl = info.portCheckerUrl;
                vm.data.featureContracts = info.featureContracts;
            });

            console.log('App version ' + info.appVersion + ', info.privateNetwork: ' + info.privateNetwork);
            $stomp.unsubscribe('/topic/initialInfo');
        }

        function onBlockchainInfoResult(data) {
            var info = (data);

            scopeUtil.safeApply(function() {
                updateBlockCounter(info.lastBlockNumber);
                vm.data.highestBlockNumber      = info.highestBlockNumber;
                vm.data.lastBlockNumber         = info.lastBlockNumber;
                vm.data.lastBlockTime           = info.lastBlockTime;
                vm.data.lastBlockTimeMoment     = moment(info.lastBlockTime * 1000).fromNow();
                vm.data.lastBlockTimeString     = moment(info.lastBlockTime * 1000).format('hh:mm:ss MMM DD YYYY');
                vm.data.lastBlockTransactions   = info.lastBlockTransactions;
                vm.data.difficulty              = filesize(info.difficulty, simpleSuffixes);
                vm.data.lastReforkTime          = info.lastReforkTime;
                vm.data.networkHashRate         = filesize(info.networkHashRate, simpleSuffixes) + 'H/s';
                vm.data.gasPrice                = formatBigDigital(info.gasPrice) + 'Wei';
                $scope.setSyncStatus(info.syncStatus);
            });
        }

        function onConfirmedTransaction(data) {
            console.log('onConfirmedTransaction');
            //console.log(data);

            if (confirmedTransactions.indexOf(data.hash) > -1) {
                console.log('Already notified tx ' + data.hash);
                return;
            }
            confirmedTransactions.push(data.hash);
            if (confirmedTransactions.length > 100) {
                confirmedTransactions.shift();
            }

            var sendMessage = data.sending ? 'SENT' : 'RECEIVED';
            var amountMessage = data.amount / Math.pow(10, 18);
            showSuccessToastr('Transaction included in block', 'Successfully ' + sendMessage + ' ' + amountMessage + ' ETH');
        }

        function disconnect() {
            connectionLostOnce = true;
            showToastr('Connection Lost', 'Reconnecting...');

            if ($stomp != null) {
                $stomp.disconnect();
            }
            setConnected(false);
            //console.log('Disconnected. Retry ...');
            setTimeout(connect, 5000);
        }

        connect();

        var loadingMessages = {
            'Headers'       : 'Validating headers',
            'BlockBodies'   : 'Loading bodies',
            'Receipts'      : 'Loading receipts'
        };

        var loadingCompleteStatuses = [
            'Headers',
            'BlockBodies',
            'Receipts',
            'Complete',
            'Off'
        ];

        $scope.setSyncStatus = function(value) {
            var oldSyncStatus = $scope.syncStatus;
            var syncStatus = $scope.syncStatus = value;

            $scope.isLoadingState = ['PivotBlock', 'StateNodes'].indexOf(syncStatus.stage) > -1;
            $scope.isLoadingStateWithBlocks = syncStatus.stage == 'StateNodes' && syncStatus.curCnt > 0 && syncStatus.curCnt == syncStatus.knownCnt;
            $scope.isSyncComplete = syncStatus.stage == 'Complete';
            $scope.isSyncOff = syncStatus.stage == 'Off';
            $scope.isRegularSync = syncStatus.stage == 'Regular';

            if (!$scope.isSyncComplete) {
                if (syncStatus.stage == 'StateNodes') {
                    $scope.syncProgressMessage = (syncStatus.curCnt + ' / ' + syncStatus.knownCnt)
                } else {
                    $scope.syncProgressMessage = loadingMessages[syncStatus.stage] || '';
                }
                if (syncStatus.knownCnt == 0 || syncStatus.stage == 'PivotBlock') {
                    $scope.loadingItemsProgress = 0;
                } else {
                    $scope.loadingItemsProgress = Math.round(100 * syncStatus.curCnt / syncStatus.knownCnt);
                }

                // state nodes loading speed
                if ($scope.isLoadingState) {
                    if ($scope.lastStateUpdateTime != 0) {
                        var oldValue = oldSyncStatus.curCnt || 0;
                        var newValue = syncStatus.curCnt;
                        var decimals = 10^0; // 0 or more digits after dot
                        var speed = Math.round(decimals * (newValue - oldValue) * 1000 / (new Date().getTime() - $scope.lastStateUpdateTime)) / decimals;
                        $scope.loadingStateSpeed = Math.max(speed, 0);
                    }
                    $scope.lastStateUpdateTime = new Date().getTime();
                }
            }
            $scope.isLoadingComplete = loadingCompleteStatuses.indexOf(syncStatus.stage) > -1;
            //console.log(syncStatus);
            //console.log($scope.isLoadingComplete);
            $scope.syncStateReceived = true;
        };
    }
})();