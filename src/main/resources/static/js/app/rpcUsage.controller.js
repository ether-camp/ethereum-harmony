/**
 * Rendering list of API usage stats.
 */

(function() {
    'use strict';

    /**
     * Updates items in array without recreating them
     */
    function synchronizeArrays(source, target, updateFun) {
        var newItems = [];
        var sourceMap = source.reduce(function(s, v) {
            s[v.methodName] = v;
            return s;
        }, {});

        // update current items
        angular.forEach(target, function(item, index) {
            var updatedItem = sourceMap[item.methodName];
            if (updatedItem) {
                updateFun(item, updatedItem);
            }
        });
        return newItems;
    }

    function RpcUsageCtrl($scope, $timeout, scrollConfig) {
        console.log('RpcUsage controller activated.');
        $scope.rpcItems = $scope.rpcItems || [];
        $scope.filterWord = '';
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
            $timeout(function() {
                // one time operation
                if ($scope.rpcItems.length == 0) {
                    angular.forEach(items, function(item) {
                        var curl = item.curl;
                        if (curl) {
                            var spaceIndex = curl.lastIndexOf(' ');
                            item.curl = curl.substr(0, spaceIndex);
                            item.curlUrl = curl.substr(spaceIndex + 1);
                        }
                    });
                    $scope.rpcItems = items;
                }
                synchronizeArrays(items, $scope.rpcItems, function(item, updatedItem) {
                    item.count = updatedItem.count;
                    item.lastResult = JSON.parse(updatedItem.lastResult);
                    item.curl = item.curl;
                    item.lastTime = updatedItem.lastTime > 0 ? moment(updatedItem.lastTime).fromNow() : '';
                });
            }, 10);
        });

        $scope.showLastResult = function(item) {
            $timeout(function() {
                item.isLastResultVisible = !item.isLastResultVisible;
            }, 10);
        };

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
