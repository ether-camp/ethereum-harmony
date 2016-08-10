/**
 * Render home page.
 *  - show blockchain tree chart;
 *
 */

(function() {
    'use strict';

    function HomeCtrl($scope, $timeout, scrollConfig) {
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        $scope.scrollConfig.axis = 'xy';

        $scope.activePeers = 0;
        $scope.syncStatus = 'n/a';
        $scope.ethPort = 'n/a';
        $scope.ethAccessible = 'n/a';

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
                    chart.setData(chartData);
                    lastRenderingTime = new Date().getTime();
                }
                timeoutPromise = $timeout(function() {
                    chart.setData(chartData);
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
                $scope.syncStatus = syncStatuses[item.syncStatus] || item.syncStatus || 'n/a';
                $scope.ethPort = item.ethPort;
                $scope.ethAccessible = item.ethAccessible;
            }, 10);
        });
        $scope.$on('connectedEvent', function (event, item) {
            // reset state
            chartData = [];
        });

        function resizeContainer() {
            console.log('Home page resize');
            var scrollContainer = document.getElementById('chart-scroll-container');
            var rect = scrollContainer.getBoundingClientRect();
            var newHeight = $(window).height() - rect.top - 20;
            //$(scrollContainer).css('maxHeight', newHeight + 'px');

            $timeout(function() {
                $scope.scrollConfig.setHeight = newHeight;
                $(scrollContainer).mCustomScrollbar($scope.scrollConfig);
            }, 10);
        }
        $(window).ready(resizeContainer);
        $scope.$on('windowResizeEvent', resizeContainer);
    }

    angular.module('HarmonyApp')
        .controller('HomeCtrl', ['$scope', '$timeout', 'scrollConfig', HomeCtrl]);
})();
