/**
 * Render home page.
 *  - show blockchain tree chart;
 *
 */

(function() {
    'use strict';




    function HomeCtrl($scope, $timeout, scrollConfig) {
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);

        var chartData = [];
        var timeoutPromise = null;
        var lastRenderingTime = new Date().getTime();

        var treeContainer = document.getElementById('blockchain-chart');

        var chart = BlockchainView.create(treeContainer);
        chart.setData(chartData);

        function addBlock(block) {
            chartData.push(block);
            if (chartData.length > 50) {
                chartData.shift();
            }
        }

        $scope.$on('newBlockInfoEvent', function(event, item) {
            addBlock(item);

            // delay rendering, but if not many blocks - first rendering should occur immediately
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
        });
        $scope.$on('currentBlocksEvent', function(event, items) {
            items.forEach(addBlock);
            chart.setData(chartData);
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
