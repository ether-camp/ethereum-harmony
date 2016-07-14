/**
 * Rendering list of peers.
 */

(function() {
    'use strict';
    angular.module('HarmonyApp')
        .controller('PeersCtrl', ['$scope', '$timeout', function($scope, $timeout) {

            var FILLED = { fillKey: "active" };
            var NOT_FILLED = { fillKey: "defaultFill" };

            console.log('Peers controller activated.');
            $scope.peers = $scope.peers || [];
            $scope.opts = {};
            $scope.peersCount = 0;

            $scope.$on('$destroy', function() {
                console.log('Peers controller exited.');
            });

            var wordmap = new Datamap({
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

            $scope.$on('peersListEvent', function(event, items) {
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

            $(window).resize(function() {
                wordmap.updateDisplay();
            });
        }]);
})();
