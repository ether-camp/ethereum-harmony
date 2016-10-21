/**
 * Allows to view contract values in blockchain storage.
 */

(function() {
    'use strict';

    var PAGE_SIZE = 5;
    var NULL = '<empty>';

    function updateEntry(entry) {
        if (entry.key && entry.value) {
            var isStruct = entry.value.typeKind == 'struct';
            entry.isCompositeObject = entry.value.container || isStruct;

            // value label
            if (entry.value.container) {
                if (entry.value.type.indexOf('mapping(') == 0) {
                    entry.valueLabel = '(size = ' + entry.value.size + ')'
                } else {
                    entry.valueLabel = /*entry.value.type + */'(size = ' + entry.value.size + ')';
                }
            } else if (isStruct) {
                entry.valueLabel = entry.value.type;
            } else {
                entry.valueLabel = entry.value.decoded ? entry.value.decoded : NULL;
            }

            // if need to show expand button
            if (entry.value.container && entry.value.size > 0) {
                entry.expandable = true;
            } else if (isStruct) {
                entry.expandable = true;
            } else {
                entry.expandable = false;
            }

        }
        return entry;
    }

    function ContractsCtrl($scope, $timeout, scrollConfig, $http) {
        console.log('Contracts controller activated.');
        $scope.contracts = $scope.contracts || [];
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        $scope.isAddingContract = false;
        $scope.isViewingStorage = false;
        $scope.newContract = {};
        $scope.storage = {entries: [], value: {decoded: ''}};

        var remove0x = Utils.Hex.remove0x;

        $scope.$on('$destroy', function() {
            console.log('Contracts controller exited.');
        });

        /**
         * Resize table to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(onResize);
        $scope.$on('windowResizeEvent', onResize);

        $scope.onWatchContract = function() {
            $scope.newContract = {};
            $scope.isAddingContract = true;
            $scope.isViewingStorage = false;
        };

        $scope.onWatchContractConfirmed = function() {
            $scope.onBackToList();
        };

        $scope.onBackToList = function() {
            $scope.isAddingContract = false;
            $scope.isViewingStorage = false;
        };

        $scope.onViewStorage = function(item) {
            $scope.isAddingContract = false;
            $scope.isViewingStorage = true;
            $scope.viewAddress = item.address;

            $http({
                method: 'GET',
                url: 'https://test-state.ether.camp/api/v1/accounts/' + remove0x($scope.viewAddress).toLowerCase() + '/smart-storage',
                params: {
                    path: '',
                    page: 0,
                    size: PAGE_SIZE
                }
            }).then(function(result) {
                $scope.contractStorage = JSON.stringify(result.data, null, 4);
                console.log(result.data);
                // copy values to keep binding working
                $scope.storage.entries = result.data.content
                    .map(updateEntry);
                $scope.storage.size = result.data.size;
                $scope.storage.number = result.data.number;
                $scope.storage.totalElements = result.data.totalElements;
            });
        };

        $scope.loadContracts = function() {
            return $http({
                method: 'GET',
                url: '/contracts/list'
            }).then(function(result) {
                console.log(result);
                $scope.contracts = (result.data || [])
                    .map(function(c) {
                        c.address = EthUtil.toChecksumAddress(c.address);
                        return c;
                    });
            });
        };

        $scope.onRemoveClick = function(item) {
            console.log('onRemoveClick');

            if (confirm("Are you sure? Stopping watching this address will remove it from listing.")) {
                $http({
                    method: 'POST',
                    url: '/contracts/' + remove0x(item.address).toLowerCase() + '/delete'
                }).then($scope.loadContracts);
            }
        };

        $scope.onWatchContractConfirmed = function() {
            $http({
                method: 'POST',
                url: '/contracts/add',
                data: {
                    address: remove0x($scope.newContract.address).toLowerCase(),
                    sourceCode: $scope.newContract.sourceCode
                }
            })
                .then($scope.loadContracts)
                .then($scope.onBackToList);
        };

        $scope.loadContracts();

        function onResize() {
            console.log("Contracts page resize");

            var scrollContainer = document.getElementById("contracts-scroll-container");
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
        .controller('ContractsCtrl', ['$scope', '$timeout', 'scrollConfig', '$http', ContractsCtrl])

        /**
         * Controller for rendering contract storage in expandable tree view.
         */
        .controller('TreeController', ['$scope', '$http', '$attrs', function($scope, $http, $attrs) {
            var remove0x = Utils.Hex.remove0x;

            function load(entry, page, size) {
                return $http({
                    method: 'GET',
                    url: 'https://test-state.ether.camp/api/v1/accounts/' + remove0x($scope.viewAddress).toLowerCase() + '/smart-storage',
                    //url: '/contracts/' + remove0x($scope.viewAddress).toLowerCase(),
                    params: {
                        path: entry.key ? entry.key.path : "",
                        page: page,
                        size: size
                    }
                }).then(function(result) {
                    console.log('Expand result');
                    console.log(result);
                    if (result.data.content.length >= entry.totalElements) {
                        // show all
                        entry.entries = result.data.content
                            .map(updateEntry);
                    } else {
                        // load more
                        var newArray = entry.entries || [];
                        Array.prototype.push.apply(newArray, result.data.content.map(updateEntry));
                        entry.entries = newArray;
                        console.log(newArray);
                    }
                });
            }

            $scope.init = function(value) {
                value.expanded = true;
                $scope.entry = value;
                //load($scope.entry, 0, PAGE_SIZE);
            };

            $scope.onShowAll = function(entry) {
                load(entry, 0, 10000);
            };

            $scope.onLoadMore = function(entry) {
                load(entry, Math.floor(entry.entries.length / PAGE_SIZE), PAGE_SIZE);
            };

            $scope.onExpand = function(entry) {
                console.log('OnExpand');
                console.log(entry);
                entry.entries = entry.entries || [];
                entry.totalElements = entry.totalElements || 0;
                load(entry, 0, PAGE_SIZE);
                entry.expanded = !entry.expanded;
            };
        }]);
})();
