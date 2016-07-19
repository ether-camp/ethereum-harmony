/**
 * Rendering list of peers.
 */

(function() {
    'use strict';

    var FILLED = { fillKey: "active" };
    var NOT_FILLED = { fillKey: "defaultFill" };

    var wordmap;    // initialized when controller starts

    function onResize() {
        console.log("Peers page resize");
        wordmap.resize();

        var scrollContainer = document.getElementById("peers-scroll-container");
        var rect = scrollContainer.getBoundingClientRect();
        var newHeight = $(window).height();
        $(scrollContainer).css('maxHeight', (newHeight - rect.top - 30) + 'px');
    }

    // update items in array without recreating them
    function synchronizeArrays(source, target, updateFun) {
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
        });
    }

    function PeersCtrl($scope, $timeout) {

        console.log('Peers controller activated.');
        $scope.peers = $scope.peers || [];
        $scope.opts = $scope.opts || {};
        $scope.peersCount = $scope.peersCount || 0;

        $scope.$on('$destroy', function() {
            console.log('Peers controller exited.');
        });

        wordmap = new Datamap({
            element: document.getElementById("serverMap"),
            fills: {
                defaultFill: "#3B3D46",
                active: "#F8A900"
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

        $scope.$on('peersListEvent', function(event, items) {
            $timeout(function() {
                synchronizeArrays(items, $scope.peers, function(oldValue, newValue) {
                    // round double value from Java
                    oldValue.pingLatency   = Math.round(newValue.pingLatency * 10) / 10;
                    oldValue.lastPing      = !newValue.lastPing ? "No info" : moment(newValue.lastPing).fromNow();
                });
                $scope.peersCount = items.length;
            }, 10);

            var colors = d3.scale.category10();
            var opts = $scope.opts;
            angular.forEach(opts, function(value, key){
                opts[key] = NOT_FILLED;
            });

            angular.forEach(items, function(value, key){
                opts[value.country3Code] = FILLED;
            });
            wordmap.updateChoropleth(opts);
            //console.log('Updated map');
        });
    }

    angular.module('HarmonyApp')
        .controller('PeersCtrl', ['$scope', '$timeout', PeersCtrl]);
})();
