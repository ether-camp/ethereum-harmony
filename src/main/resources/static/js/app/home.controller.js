/**
 * Render home page.
 *  - show blockchain tree chart;
 *  - show blockchain info
 */

(function() {
    'use strict';

    function HomeCtrl($scope, $timeout, $http, scrollConfig) {
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        //$scope.scrollConfig.axis = 'xy';
        $scope.scrollConfig.scrollbarPosition = 'outside';
        $scope.scrollConfig.scrollInertia = 200;

        $scope.activePeers = 0;
        $scope.syncStatus = 'n/a';
        $scope.ethPort = 'n/a';
        $scope.ethAccessible = 'n/a';
        $scope.miners = [];
        $scope.isLongSync = false;

        var syncStatuses = {
            'LONG_SYNC': 'Long sync',
            'SHORT_SYNC': 'Short sync',
            'DISABLED': 'Sync disabled'
        };

        var chartData = [];
        var timeoutPromise = null;
        var lastRenderingTime = new Date().getTime();

        var treeContainer = document.getElementById('blockchain-chart');

        var chart = BlockchainView.create(treeContainer);
        redrawChartLater();

        function addBlock(block) {
            var isExist = chartData.some(function(b) {
                return b.blockHash == block.blockHash;
            });
            if (!isExist) {
                chartData.push(block);
                if (chartData.length > 50) {
                    chartData.shift();
                }
            }
        }

        /**
         * Delay rendering, but if not many blocks - first rendering should occur immediately
         */
        function redrawChartLater() {
            if (!timeoutPromise) {
                if (new Date().getTime() - 2000 > lastRenderingTime) {
                    chart.addBlocks(chartData);
                    chartData = [];
                    lastRenderingTime = new Date().getTime();
                }
                timeoutPromise = $timeout(function() {
                    chart.addBlocks(chartData);
                    chartData = [];
                    lastRenderingTime = new Date().getTime();
                    timeoutPromise = null;
                }, 2000);
            }
        }

        $scope.$on('newBlockInfoEvent', function(event, item) {
            addBlock(item);

            redrawChartLater();
        });
        $scope.$on('currentBlocksEvent', function(event, items) {
            items.forEach(addBlock);
            redrawChartLater();
        });
        $scope.$on('networkInfoEvent', function(event, item) {
            $timeout(function() {
                $scope.activePeers = item.activePeers;
                $scope.syncStatus = syncStatuses[item.syncStatus] || syncStatuses[item.syncStatus] || 'n/a';
                $scope.ethPort = item.ethPort;
                $scope.ethAccessible = item.ethAccessible;
                $scope.miners = item.miners;
                $scope.isLongSync = item.syncStatus == 'LONG_SYNC';
            }, 10);
        });
        $scope.$on('connectedEvent', function (event, item) {
            // reset state
            chartData = [];
        });


        function resizeContainer() {
            console.log('Home page resize');
            [{id:'miners-scroll-container', axis:'y'}, {id:'chart-scroll-container', axis:'xy'}].forEach(function(item) {
                var scrollContainer = document.getElementById(item.id);
                var rect = scrollContainer.getBoundingClientRect();
                var newHeight = $(window).height() - rect.top - 20;
                //$(scrollContainer).css('maxHeight', newHeight + 'px');

                $timeout(function() {
                    $scope.scrollConfig.setHeight = newHeight;
                    $scope.scrollConfig.axis = item.axis;
                    $(scrollContainer).mCustomScrollbar($scope.scrollConfig);
                }, 10);
            });
        }

        $.fn.animateBlinking = function(duration) {
            this.fadeIn(duration).fadeOut(duration).fadeIn(duration).fadeOut(duration).fadeIn(duration);
        };

        $(window).ready(function() {

            function testPortFun(protocol, portFun, elem) {
                function markResult(success) {
                    elem.stop(true, true);
                    elem.fadeIn(1);
                    elem.parent()
                        .find($('.port-test-result'))
                        .text(success ? 'v' : 'x')
                        .css('color', success ? 'green' : 'red');
                }

                return function() {
                    var url = $scope.vm.data.portCheckerUrl + '/checkPort';
                    var data = {port: portFun(), protocol: protocol};

                    elem.animateBlinking(500);
                    $http({
                        url: url,
                        method: 'POST',
                        data: data
                    }).then(
                        function (response) {
                            console.log(response);
                            markResult(response && response.data && response.data.result);
                        },
                        function (error) { // optional
                            console.log(error);
                            markResult(false);
                        });
                }
            }

            $('#tcpTestLink').click(testPortFun('tcp', function() { return $scope.vm.data.rpcPort + 0; }, $('#tcpTestLink')));
            $('#udpTestLink').click(testPortFun('udp', function() { return $scope.ethPort} , $('#udpTestLink')));

            resizeContainer();
        });
        $scope.$on('windowResizeEvent', resizeContainer);
    }

    angular.module('HarmonyApp')
        .controller('HomeCtrl', ['$scope', '$timeout', '$http', 'scrollConfig', HomeCtrl])
        .filter('range', function() {
            return function(val, range) {
                range = parseInt(range);
                for (var i=0; i<range; i++)
                    val.push(i);
                return val;
            };
        });
})();
