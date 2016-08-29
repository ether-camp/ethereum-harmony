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
        var arr = x.toString().split('.');
        var arr1 = arr[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        return arr.length == 1 ? arr1 : arr1 + '.' + arr[1];
    }

    function hexToInt(hexValue) {
        return parseInt(remove0x(hexValue), 16);
    }

    function remove0x(value) {
        return (value && value.indexOf('0x') == 0) ? value.substr(2) : value;
    }


    function WalletCtrl($scope, $timeout, $stomp, $http, jsonrpc, $q, scrollConfig, ModalService) {
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);

        $scope.totalAmount = 0;
        $scope.totalAmountString = 0;
        $scope.totalAmountUSD = 'n/a';
        $scope.addresses = [];
        $scope.txData = {};

        $scope.importAddressData = {};
        $scope.newAddressData = {};

        $scope.onSendClick = function(item) {
            console.log('onSendClick');
            ModalService.showModal({
                templateUrl:    "pages/popups/sendAmount.html",
                controller:     "SendAmountCtrl",
                inputs:         {item: item}
            })
                .then(function(modal) {

                    modal.element.modal();
                    modal.close.then(function(result) {
                        $scope.message = result ? "You said Yes" : "You said No";
                    });
                    console.log('SendAmount constructed');
                });
        };

        $scope.onRemoveClick = function(item) {
            console.log('onRemoveClick');

            if (confirm("Delete address with its keystore file?")) {
                $stomp.send('/app/removeAddress', {
                    value:      item.publicAddress
                });
            }
        };

        $scope.onNewAddress = function() {
            console.log('onNewAddress');

            $scope.newAddressData = {
                password:   '',
                name:       ''
            };
            $('#newAddressModal').modal({});
        };

        $scope.onImportAddress = function() {
            console.log('onImportAddress');

            $scope.importAddressData = {
                address:    '',
                name:       ''
            };
            $('#importAddressModal').modal({});
        };

        $scope.onImportAddressConfirmed = function() {
            console.log('onImportAddressConfirmed');
            $stomp.send('/app/importAddress', $scope.importAddressData);

            $('#importAddressModal').modal('hide');
        };


        function resizeContainer() {
            console.log('Wallet page resize');

            var scrollContainer = document.getElementById("address-scroll-container");
            var rect = scrollContainer.getBoundingClientRect();
            var newHeight = $(window).height() - rect.top - 30;
            //$(scrollContainer).css('maxHeight', newHeight + 'px');
            $timeout(function() {
                $scope.scrollConfig.setHeight = newHeight;
                $(scrollContainer).mCustomScrollbar($scope.scrollConfig);
            }, 10);
        }

        $scope.$on('walletInfoEvent', function(event, data) {
            console.log('walletInfoEvent');
            console.log(data);

            $timeout(function() {
                $scope.totalAmount = data.totalAmount;
                $scope.totalAmountString = numberWithCommas(data.totalAmount / ETH_BASE);
                data.addresses.forEach(function(a) {
                    a.amount = numberWithCommas(a.amount / ETH_BASE);
                });
                $scope.addresses = data.addresses;
            }, 10);

            $http({
                method: 'GET',
                url: 'https://coinmarketcap-nexuist.rhcloud.com/api/eth'
            }).then(function(result) {
                try {
                    var price = result.data.price.usd;
                    $scope.totalAmountUSD = numberWithCommas((data.totalAmount / ETH_BASE) * price);
                } catch (e) {
                    // silent
                    console.log('Problem loading market value of ETH. ' + e)
                }
            })
        });


        $(window).ready(function() {
            // Every time a modal is shown, if it has an autofocus element, focus on it.
            $('.modal').on('shown.bs.modal', function() {
                $(this).find('[autofocus]').focus();
            });

            resizeContainer();
        });
        $scope.$on('windowResizeEvent', resizeContainer);
    }

    angular.module('HarmonyApp')
        .controller('WalletCtrl', ['$scope', '$timeout', '$stomp', '$http', 'jsonrpc', '$q', 'scrollConfig', 'ModalService', WalletCtrl])

})();
