/**
 * Render home page.
 *  - show blockchain tree chart;
 *  - show blockchain info
 */

(function() {
    'use strict';

    /**
     * @example formatWithProps('Hello {name}', {name: "Stan"}) will produce 'Hello Stan'
     */
    function formatWithProps(input, map) {
        if (!input) {
            return '';
        }
        var result = input;
        for (var prop in map) {
            result = result.replace('{' + prop + '}', map[prop]);
        }
        return result;
    }

    function HomeCtrl($scope, $timeout, $http, $q, scrollConfig) {
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        //$scope.scrollConfig.axis = 'xy';
        $scope.scrollConfig.scrollbarPosition = 'outside';
        $scope.scrollConfig.scrollInertia = 200;

        $scope.activePeers = 0;
        $scope.syncStatus = 'n/a';
        $scope.syncStatusMessageTop = '';
        $scope.syncStatusMessageBottom = '';
        $scope.ethPort = 'n/a';
        $scope.ethAccessible = 'n/a';
        $scope.miners = [];
        $scope.publicIp = ' ';

        var syncStatuses = {
            'PivotBlock': 'Preparing for fast sync',
            'StateNodes': 'Fast sync',
            'Headers': 'Synced. Headers ({curCnt} of {knownCnt})',
            'BlockBodies': 'Synced. Bodies ({curCnt} of {knownCnt})',
            'Receipts': 'Synced. Receipts ({curCnt} of {knownCnt})',
            'Regular': 'Long sync',
            'Complete': 'Short sync',
            'Off': 'Disabled'
        };

        var syncStatusesMessageTop = {
            'PivotBlock': 'Loading state in fast sync mode.',
            'StateNodes': 'Loading state in fast sync mode.',
            'Regular': 'Loading state in long sync mode.'
        };

        var syncStatusesMessageBottom = {
            'PivotBlock': 'Preparing for fast sync. Best known block {blockBestKnown}.',
            'StateNodes': 'Imported {curCnt} of {knownCnt} known state nodes.',
            // this one happens when state has been loaded and blocks are loading till last
            'StateNodesBlocks': 'State loaded. Imported {blockLastImported} of {blockBestKnown} known best block.',
            'Regular': 'Imported {blockLastImported} blocks, highest known block is {blockBestKnown}.'
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
                $scope.syncStatus = formatWithProps(syncStatuses[item.syncStatus.stage], item.syncStatus) || item.syncStatus.stage || 'n/a';
                $scope.syncStatusMessageTop = formatWithProps(syncStatusesMessageTop[item.syncStatus.stage], item.syncStatus);
                if (item.syncStatus.stage == 'StateNodes' && item.syncStatus.curCnt > 0 && item.syncStatus.curCnt == item.syncStatus.knownCnt) {
                    $scope.syncStatusMessageBottom = formatWithProps(syncStatusesMessageBottom['StateNodesBlocks'], item.syncStatus);
                } else {
                    $scope.syncStatusMessageBottom = formatWithProps(syncStatusesMessageBottom[item.syncStatus.stage], item.syncStatus);
                }
                $scope.ethPort = item.ethPort;
                $scope.ethAccessible = item.ethAccessible;
                $scope.miners = item.miners;

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
            var originColor = $('.port-test-result').eq(0).css('color');

            $('#testPortsLink').click(function() {
                if (isPortCheckingProgress) {
                    console.log('Test is already in progress');
                    return false;
                }
                isPortCheckingProgress = true;

                var elem = $('#testPortsLink');
                //elem.animateBlinking(500);

                function markResult(elem, success, address, isError) {
                    elem.stop(true, false);
                    elem.fadeIn(1);
                    if (isError) {
                        elem.text('service issue')
                    } else {
                        elem
                            .text(success ? 'v' : 'x')
                            .css('color', success ? 'green' : 'red');
                        $scope.vm.data.publicIp = address;
                        $scope.vm.data.publicIpLabel = 'Public IP'
                    }
                }

                var ports = [$scope.ethPort, $scope.vm.data.rpcPort];
                var promises = ports
                    .map(function(port, i) {
                        var url = $scope.vm.data.portCheckerUrl + '/checkPort';
                        var data = { port: port, protocol: 'tcp' };

                        if (!(parseInt(port) > 0)) {
                            console.log('Not valid port ' + port);
                            return $q.resolve(true);
                        }

                        var resultElem = $('.port-test-result').eq(i);
                        resultElem.animateBlinking(500);
                        resultElem
                            .text('?')
                            .css('color', originColor);

                        return $http({
                            url: url,
                            method: 'POST',
                            data: data
                        }).then(
                            function (response) {
                                console.log(response);
                                var result = response && response.data && response.data.result;
                                markResult(resultElem, result, response.data ? response.data.address : null, false);
                                return response;
                            },
                            function (error) {
                                console.log(error);
                                markResult(resultElem, false, null, true);
                                return error;
                            });
                    });
                $q.all(promises).then(function() {
                    isPortCheckingProgress = false;
                    elem.stop(true, false);
                    elem.fadeOut(100);
                    elem.fadeIn(100);
                });
            });

            resizeContainer();
        });
        $scope.$on('windowResizeEvent', resizeContainer);
    }

    angular.module('HarmonyApp')
        .controller('HomeCtrl', ['$scope', '$timeout', '$http', '$q', 'scrollConfig', HomeCtrl])
        .filter('range', function() {
            return function(val, range) {
                range = parseInt(range);
                for (var i=0; i<range; i++)
                    val.push(i);
                return val;
            };
        });
})();
