/**
 * Rendering list of API usage stats.
 */

(function() {
    'use strict';

    function onResize() {
        console.log("RpcUsage page resize");

        var scrollContainer = document.getElementById("rpc-scroll-container");
        var rect = scrollContainer.getBoundingClientRect();
        var newHeight = $(window).height();
        $(scrollContainer).css('maxHeight', (newHeight - rect.top - 30) + 'px');
    }

    function RpcUsageCtrl($scope, $timeout) {
        console.log('RpcUsage controller activated.');
        var testData = [];
        for(var i=0; i < 100; i++){
            testData.push({methodName: '/net_version' + i, count: i, lastTime: '2 mins ago'});
        }
        $scope.rpcItems = $scope.rpcItems || testData;

        $scope.$on('$destroy', function() {
            console.log('RpcUsage controller exited.');
        });

        /**
         * Resize table to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(onResize);
        $scope.$on('windowResizeEvent', onResize);

        /**
         * Received stats update from server
         */
        $scope.$on('rpcUsageListEvent', function(event, items) {
            angular.forEach(items, function(item) {
                item.lastTime = item.lastTime > 0 ? moment(item.lastTime).fromNow() : '';
            });

            $timeout(function() {
                $scope.rpcItems = items;
            }, 10);
        });
    }

    angular.module('HarmonyApp')
        .controller('RpcUsageCtrl', ['$scope', '$timeout', RpcUsageCtrl]);
})();
