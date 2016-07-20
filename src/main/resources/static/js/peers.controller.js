/**
 * Rendering list of peers.
 */

(function() {
    'use strict';

    var FILLED = { fillKey: "filled" };
    var NOT_FILLED = { fillKey: "defaultFill" };
    var BLINKED = { fillKey: "blink" };

    var wordmap;    // initialized when controller starts

    function onResize() {
        console.log("Peers page resize");
        wordmap.resize();

        var scrollContainer = document.getElementById("peers-scroll-container");
        var rect = scrollContainer.getBoundingClientRect();
        var newHeight = $(window).height();
        $(scrollContainer).css('maxHeight', (newHeight - rect.top - 30) + 'px');
    }

    /**
     * Updates items in array without recreating them
     */
    function synchronizeArrays(source, target, updateFun) {
        var newPeers = [];
        var sourceMap = source.reduce(function(s, v) {
            s[v.nodeId] = v;
            return s;
        }, {});
        var indexesForRemove = [];
        // update current items
        angular.forEach(target, function(item, index) {
            var updatedItem = sourceMap[item.nodeId];
            if (updatedItem) {
                updateFun(item, updatedItem);
                delete sourceMap[item.nodeId];
            } else {
                indexesForRemove.push(index);
            }
        });

        // remove not existing items
        angular.forEach(
            // sorted DESC indexes
            indexesForRemove.sort(function(a,b) {
                return a - b;
            }),
            // remove by index
            function(index) {
                target.splice(index, 1)
            });

        // add new items
        angular.forEach(sourceMap, function(item) {
            updateFun(item, item);
            target.push(item);
            newPeers.push(item);
        });
        return newPeers;
    }

    function PeersCtrl($scope, $timeout) {

        console.log('Peers controller activated.');
        $scope.peers = $scope.peers || [];
        $scope.opts = $scope.opts || {};
        $scope.peersCount = $scope.peersCount || 0;
        $scope.showActive = true;
        $scope.showInactive = false;

        $scope.$on('$destroy', function() {
            console.log('Peers controller exited.');
        });

        wordmap = new Datamap({
            element: document.getElementById("serverMap"),
            fills: {
                defaultFill: "#3B3D46",
                filled: "#CA8800",
                blink: "#FFF108"
            },
            responsive: true,
            geographyConfig: {
                highlightOnHover: false,
                borderWidth: 0
            },
            data: {
                //USA: { fillKey: "active" }
            }
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
        $scope.$on('peersListEvent', function(event, items) {
            $timeout(function() {
                // #1 Update List
                var newPeers = synchronizeArrays(items, $scope.peers, function(oldValue, newValue) {
                    // round double value from Java
                    oldValue.pingLatency    = Math.round(newValue.pingLatency * 10) / 10;
                    oldValue.lastPing       = !newValue.lastPing ? "No info" : Math.round(newValue.lastPing / 1000) + " seconds ago";
                    oldValue.isActive       = newValue.isActive;
                });
                $scope.peersCount = items.length;

                // #2 Update Map
                //var opts = $scope.opts;
                var opts = $scope.opts;
                angular.forEach(opts, function(value, key){
                    opts[key] = NOT_FILLED;
                });
                angular.forEach(items, function(value, key){
                    opts[value.country3Code] = FILLED;
                });

                angular.forEach(newPeers, function(value, key){
                    opts[value.country3Code] = BLINKED;
                });

                wordmap.updateChoropleth(opts);

                $timeout(function() {
                    angular.forEach(opts, function(value, key){
                        if (value == BLINKED) {
                            opts[key] = FILLED;
                        }
                    });

                    wordmap.updateChoropleth(null);
                }, 500);
            }, 10);
        });

        $scope.onShowActiveChange = function() {

        };
        $scope.onShowInactiveChange = function() {

        };
    }

    angular.module('HarmonyApp')
        .controller('PeersCtrl', ['$scope', '$timeout', PeersCtrl]);
})();
