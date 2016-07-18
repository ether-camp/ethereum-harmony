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
            angular.forEach(items, function(value, key) {
                // round double value from Java
                value.pingLatency = Math.round(value.pingLatency * 10) / 10;
            });

            $timeout(function() {
                $scope.peers = items;
                $scope.peersCount = items.length;
            }, 10);

            var colors = d3.scale.category10();
            var opts = $scope.opts;
            angular.forEach(opts, function(value, key){
                opts[key] = NOT_FILLED;
            });

            angular.forEach(items, function(value, key){
                opts[value.country] = FILLED;
            });
            wordmap.updateChoropleth(opts);
            console.log('Updated map');
        });
    }

    angular.module('HarmonyApp')
        .controller('PeersCtrl', ['$scope', '$timeout', PeersCtrl]);
})();
