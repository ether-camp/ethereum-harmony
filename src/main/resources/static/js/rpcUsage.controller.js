/**
 * Rendering list of API usage stats.
 */

(function() {
    'use strict';

    function RpcUsageCtrl($scope, $timeout, scrollConfig) {
        console.log('RpcUsage controller activated.');
        $scope.rpcItems = $scope.rpcItems;
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);

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

        function onResize() {
            console.log("RpcUsage page resize");

            var scrollContainer = document.getElementById("rpc-scroll-container");
            var rect = scrollContainer.getBoundingClientRect();
            var newHeight = $(window).height() - rect.top - 20;
            //$(scrollContainer).css('maxHeight', newHeight + 'px');
            $scope.scrollConfig.setHeight = newHeight;
            $timeout(function() {
                $(scrollContainer).mCustomScrollbar($scope.scrollConfig);
            }, 10);
        }
    }

    angular.module('HarmonyApp')
        .controller('RpcUsageCtrl', ['$scope', '$timeout', 'scrollConfig', RpcUsageCtrl]);
})();
