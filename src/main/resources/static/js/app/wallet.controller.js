/**
 * Render home page.
 *  - show blockchain tree chart;
 *  - show blockchain info
 */

(function() {
    'use strict';

    var ETH_BASE = 1000000000;

    /**
     * @example 1000 -> "1,000"
     */
    function numberWithCommas(x) {
        return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    }

    function WalletCtrl($scope, $timeout, $stomp, $http, $q, scrollConfig) {
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);

        $scope.totalAmount = 0;
        $scope.totalAmountUSD = 0;
        $scope.addresses = [];

        if ($scope.vm.data.isConnected) {
            onSocketConnected();
        }
        $scope.$on('connectedEvent', onSocketConnected);


        function resizeContainer() {
            console.log('Wallet page resize');
        }

        function onSocketConnected(event, item) {
            $stomp.subscribe('/topic/getWalletInfo', function(info) {
                console.log(info);

                $timeout(function() {
                    $scope.totalAmount = numberWithCommas(info.totalAmount / ETH_BASE);
                    info.addresses.forEach(function(a) {
                        a.amount = numberWithCommas(a.amount / ETH_BASE);
                    });
                    $scope.addresses = info.addresses;
                }, 10);
            });
            $stomp.send('/app/getWalletInfo');
        }


        $(window).ready(function() {
            resizeContainer();
        });
        $scope.$on('windowResizeEvent', resizeContainer);
    }

    angular.module('HarmonyApp')
        .controller('WalletCtrl', ['$scope', '$timeout', '$stomp', '$http', '$q', 'scrollConfig', WalletCtrl])

})();
