/**
 * Allows to view contract values in blockchain storage.
 */

(function() {
    'use strict';

    function ContractsCtrl($scope, $timeout, scrollConfig, $http) {
        console.log('Contracts controller activated.');
        $scope.contracts = $scope.contracts || [{name: "A", address: "0x123456789"}];
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        $scope.isAddingContract = false;
        $scope.isViewingStorage = false;
        $scope.newContract = {};
        $scope.storage = {};

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
            $scope.viewAddress = item.address

            $http({
                method: 'GET',
                url: '/contracts/' + remove0x($scope.viewAddress).toLowerCase()
            }).then(function(result) {
                $scope.contractStorage = JSON.stringify(result.data, null, 4);
                console.log(result.data);
                $scope.storage.entries = result.data.entries
                    .map(function(e) {
                        return e;
                    });
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
        .controller('TreeController', ['$scope', '$http', function($scope, $http) {
            var remove0x = Utils.Hex.remove0x;

            $scope.onExpand = function(entry) {
                console.log('Expand ');
                console.log(entry);

                $http({
                    method: 'GET',
                    url: '/contracts/' + remove0x($scope.viewAddress).toLowerCase(),
                    params: {
                        path: entry.key.path
                    }
                }).then(function(result) {
                    console.log('Expand result ');
                    console.log(result.data);
                    entry.entries = result.data.entries;
                });
            };
        }])
        .directive('ehStorageEntry', function() {
            return {
                restrict: 'E',
                scope: {
                    entry: '=entry'
                },
                templateUrl: 'pages/templates/storage-entry.html'
            };
        });
})();
