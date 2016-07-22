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
        $scope.items = $scope.items || [{methodName: '/net_version', count: 44, lastTime: '2 mins ago'}];

        $scope.$on('$destroy', function() {
            console.log('RpcUsage controller exited.');
        });

        /**
         * Resize peers table to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(onResize);
        $scope.$on('windowResizeEvent', onResize);

        /**
         * Received peers list update from server
         */
        $scope.$on('rpcUsageListEvent', function(event, items) {
            $timeout(function(items) {
                $scope.items = items;
            }, 10);
        });
    }

    angular.module('HarmonyApp')
        .controller('RpcUsageCtrl', ['$scope', '$timeout', RpcUsageCtrl]);
})();
