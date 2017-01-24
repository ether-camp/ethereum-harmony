/**
 * Rendering list of peers.
 *  - show table of active/non active peers;
 *  - show world map of where peers are located;
 *
 */

(function() {
    'use strict';

    var FILLED = { fillKey: "filled" };
    var NOT_FILLED = { fillKey: "defaultFill" };
    var BLINKED = { fillKey: "blink" };

    var wordmap;    // initialized when controller starts


    /**
     * Updates items in array without recreating them
     */
    function synchronizeArrays(source, target, updateFun) {
        var newItems = [];
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
            newItems.push(item);
        });
        return newItems;
    }

    function getPingString(value) {
        value =  Math.ceil((value) / 1000);
        if (value > 59) {
            return Math.ceil(value / 60) + ' min ago';
        }
        return value + ' sec ago';
    }

    function PeersCtrl($scope, $timeout, scrollConfig) {

        console.log('Peers controller activated.');
        $scope.peers = $scope.peers || [];
        $scope.opts = $scope.opts || {};
        $scope.peersCount = $scope.peersCount || 0;
        $scope.activePeersCount = $scope.activePeersCount || 0;
        $scope.showActive = true;
        $scope.showInactive = false;
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);

        $scope.hidePeerDetails = true;
        $scope.peerDetails = '';
        $scope.selectedPeer = null;

        $scope.onOverPeer = function(node) {
            $scope.peerDetails = getPeerDetails(node);
            $scope.hidePeerDetails = false;
        };
        $scope.onOutPeer = function(node) {
            if ($scope.selectedPeer) {
                $scope.peerDetails = getPeerDetails($scope.selectedPeer);
            } else {
                $scope.hidePeerDetails = true;
            }
        };

        $scope.onClickPeer = function(node) {
            $scope.selectedPeer = node;
            $scope.peerDetails = getPeerDetails(node);
            $scope.hidePeerDetails = false;
        };

        $scope.$on('$destroy', function() {
            $timeout.cancel($scope.promise);
            console.log('Peers controller exited.');
        });

        wordmap = new Datamap({
            element: document.getElementById("serverMap"),
            fills: {
                defaultFill: "#3B3D46",
                filled: "#CA8800",
                blink: "#FFF108",
                block: "#FF0000"
            },
            responsive: true,
            geographyConfig: {
                highlightOnHover: false,
                borderWidth: 0
            },
            bubblesConfig: {
                borderWidth: 1,
                borderOpacity: 1,
                borderColor: '#FFFFFF',
                popupOnHover: true, // True to show the popup while hovering
                radius: 5,
                popupTemplate: function(geography, data) { // This function should just return a string
                    return '<div class="hoverinfo"><strong>' + data.name + '</strong></div>';
                },
                fillOpacity: 0.75,
                animate: true,
                highlightOnHover: true,
                highlightFillColor: '#FF585B',
                highlightBorderColor: '#FFFFFF',
                highlightBorderWidth: 1,
                highlightBorderOpacity: 1,
                highlightFillOpacity: 0.85,
                exitDelay: 100, // Milliseconds
                key: JSON.stringify
            },
            legend: true,
            data: {
                //USA: { fillKey: "active" }
            }
        });
        //wordmap.legend();

        /**
         * Resize table to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(onResize);
        $scope.$on('windowResizeEvent', onResize);

        /**
         * Received peers list update from server
         */
        $scope.$on('peersListEvent', function(event, items) {
            $timeout(function() {
                var timeNow = new Date().getTime();
                // #1 Update List
                var newPeers = synchronizeArrays(items, $scope.peers, function(oldValue, newValue) {
                    // round double value from Java
                    oldValue.pingLatency    = Math.round(newValue.pingLatency * 10) / 10;
                    oldValue.lastPing       = newValue.lastPing ? getPingString(timeNow - newValue.lastPing) : '';
                    oldValue.isActive       = newValue.isActive;
                });
                $scope.peersCount = items.length;
                var activePeersCount = 0;
                angular.forEach(items, function(item){
                    if (item.isActive) {
                        activePeersCount++;
                    }
                });
                $scope.activePeersCount = activePeersCount;

                // #2 Update Map
                //var opts = $scope.opts;
                var opts = $scope.opts;
                angular.forEach(opts, function(value, key){
                    opts[key] = NOT_FILLED;
                });

                // show only active peers on map
                angular.forEach(items, function(value, key){
                    if (value.isActive) {
                        opts[value.country3Code] = FILLED;
                    }
                });

                // blink only when not first update
                if (newPeers.length != items.length) {
                    angular.forEach(newPeers, function(value, key){
                        if (value.isActive) {
                            opts[value.country3Code] = BLINKED;
                        }
                    });
                }

                wordmap.updateChoropleth(opts);

                $timeout(function() {
                    angular.forEach(opts, function(value, key){
                        if (value == BLINKED) {
                            opts[key] = FILLED;
                        }
                    });

                    wordmap.updateChoropleth(opts);
                }, 700);
            }, 10);
        });

        $scope.$on('newBlockFromEvent', function(event, item) {
            //console.log("New block from " + item.country3Code);

            $timeout.cancel($scope.promise);

            wordmap.bubbles([
                {
                    name: 'New block from ' + item.country3Code,
                    centered: item.country3Code,
                    country: item.country3Code,
                    fillKey: 'block'
                }
            ], {exitDelay: 1000});

            $scope.promise = $timeout(function() {
                wordmap.bubbles([], {exitDelay: 200});
            }, 1000);
        });

        $scope.onShowActiveChange = function() {

        };
        $scope.onShowInactiveChange = function() {

        };

        function getPeerDetails(item) {
            return item ? item.details : '';
        }

        function onResize() {
            console.log("Peers page resize");
            wordmap.resize();

            var scrollContainer = document.getElementById("peers-scroll-container");
            var rect = scrollContainer.getBoundingClientRect();
            var newHeight = $(window).height() - rect.top - 30;
            //$(scrollContainer).css('maxHeight', newHeight + 'px');
            $timeout(function() {
                $scope.scrollConfig.setHeight = newHeight;
                $(scrollContainer).mCustomScrollbar($scope.scrollConfig);
            }, 10);
        }
    }

    angular.module('HarmonyApp')
        .controller('PeersCtrl', ['$scope', '$timeout', 'scrollConfig', PeersCtrl]);
})();
