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

        // loop fadeOut / fadeIn animation
        // can be stopped with elem.stop(true, false)
        $.fn.animateBlinking = function(duration) {
            function doAnimation(elem) {
                console.log('Start doAnimation')
                elem.fadeIn(duration).fadeOut(duration, function() {
                    doAnimation(elem);
                });
            }
            doAnimation(this);
        };

        $(window).ready(function() {

            /**
             * Logic bellows handles "Test port" link click:
             *  - start infinite link blinking and reset result indicator;
             *  - request 3rd party server to scan this server TCP port, by sending POST;
             *  - stop blinking on respond and indicate result in UI
             *  (jQuery, jQuery-animate, angular-http)
             */

            var isPortCheckingProgress = false;

            function testPortFun(protocol, portFun, elem) {
                var resultElem =  elem.parent()
                    .find($('.port-test-result'));
                var addressElem =  elem.parent()
                    .find($('.port-test-address'));
                var originColor = resultElem.css('color');

                function markResult(success, address, isError) {
                    elem.stop(true, false);
                    elem.fadeIn(1);
                    if (isError) {
                        resultElem
                            .text('service issue')
                    } else {
                        resultElem
                            .text(success ? 'v' : 'x')
                            .css('color', success ? 'green' : 'red');
                        addressElem.text(address + ' - ');
                    }
                    isPortCheckingProgress = false;
                }

                return function() {
                    if (isPortCheckingProgress) {
                        console.log('Test is already in progress');
                        return false;
                    }
                    isPortCheckingProgress = true;

                    var url = $scope.vm.data.portCheckerUrl + '/checkPort';
                    // force TCP check regardless UDP was requested
                    var data = { port: portFun(), protocol: protocol };

                    resultElem
                        .text('?')
                        .css('color', originColor);
                    addressElem.text('');
                    elem.animateBlinking(500);
                    $http({
                        url: url,
                        method: 'POST',
                        data: data
                    }).then(
                        function (response) {
                            console.log(response);
                            var result = response && response.data && response.data.result;
                            markResult(result, response.data ? response.data.address : null, false);
                        },
                        function (error) {
                            console.log(error);
                            markResult(false, null, true);
                        });
                }
            }

            $('#tcpTestLink').click(testPortFun('tcp', function() { return $scope.vm.data.rpcPort; }, $('#tcpTestLink')));
            $('#udpTestLink').click(testPortFun('tcp', function() { return $scope.ethPort} , $('#udpTestLink')));

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
