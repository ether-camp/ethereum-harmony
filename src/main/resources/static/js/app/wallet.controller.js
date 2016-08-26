/**
 * Render home page.
 *  - show blockchain tree chart;
 *  - show blockchain info
 */

(function() {
    'use strict';

    var ETH_BASE = 1000000000;
    var defaultCurrency = 'wei';   // otherwise need to convert

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
        if (value && value.indexOf('0x') == 0) {
            return value.substr(2);
        } else {
            return value;
        }
    }

    function showErrorToastr(topMessage, bottomMessage) {
        toastr.clear()
        toastr.options = {
            "positionClass": "toast-top-right",
            "closeButton": true,
            "progressBar": true,
            "showEasing": "swing",
            "timeOut": "4000"
        };
        toastr.error('<strong>' + topMessage + '</strong> <br/><small>' + bottomMessage + '</small>');
    }


    function WalletCtrl($scope, $timeout, $stomp, $http, jsonrpc, $q, scrollConfig) {
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);

        $scope.totalAmount = 0;
        $scope.totalAmountString = 0;
        $scope.totalAmountUSD = 'n/a';
        $scope.addresses = [];
        $scope.txData = {};

        $scope.importAddressData = {};
        $scope.newAddressData = {};

        $scope.onSendClick = function(address) {
            console.log('onSendClick');
            $scope.txData = {
                fromAddress: address.publicAddress,
                toAddress: '',
                amount: 0
            };
            $('#sendAmountModal').modal({});
        };

        $scope.onRemoveClick = function(item) {
            console.log('onRemoveClick');

            $stomp.send('/app/removeAddress', {
                value:      item.publicAddress
            });
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

        $scope.onSignAndSend = function() {
            var privateKey = $('#pkeyInput').val();
            var amount = $scope.txData.amount * Math.pow(10, 18);
            var txData = $scope.txData;

            console.log('Before sign and send amount:' + amount);
            console.log(txData);

            $q.all([
                jsonrpc.request('eth_gasPrice', []),
                jsonrpc.request('eth_getTransactionCount', ['0x' + txData.fromAddress, 'latest'])
            ])
                .then(function(results) {
                    console.log(results);

                    var gasPrice = parseInt(remove0x(results[0]), 16);
                    var nonce = parseInt(remove0x(results[1]), 16);
                    var gasLimit =  21000;

                    return RlpBuilder
                        .balanceTransfer(remove0x(txData.toAddress))
                        .from(remove0x(txData.fromAddress))
                        .secretKey(privateKey)
                        .gasLimit(gasLimit)
                        .gasPrice(gasPrice)
                        .value(amount, defaultCurrency)
                        .nonce(nonce)
                        .withData('')
                        .format();
                })
                .then(function (rlp) {
                    console.log('Signed transaction');
                    console.log(rlp);

                    return jsonrpc.request('eth_sendRawTransaction', [rlp]);
                        //.catch(function(error) {
                        //    console.log('Error sending raw transaction');
                        //    console.log(error);
                        //    showErrorToastr('ERROR', 'Wasn\'t to send signed raw transaction.\n' + error);
                        //});
                })
                .then(function(txHash) {
                    console.log('eth_sendRawTransaction result:' + txHash);

                    $('#sendAmountModal').modal('hide');
                    // load updated state
                    $stomp.send('/app/getWalletInfo');

                    return jsonrpc.request('ethj_getTransactionReceipt', [txHash]);
                })
                .then(function(txReceipt) {
                    console.log('ethj_getTransactionReceipt result');
                    console.log(txReceipt);

                    var errorMessage = txReceipt ? txReceipt.error : 'Unknown error during load transaction receipt.';
                    if (errorMessage) {
                        showErrorToastr(errorMessage);
                    }
                })

                .catch(function(error) {
                    console.log('Error signing tx ' + error);
                    showErrorToastr('ERROR', 'Wasn\'t able to sign or send transaction.\n' + error);
                    //$balanceDlg.find('input[name="key"]').addClass('error').focus();
                });
        };

        function resizeContainer() {
            console.log('Wallet page resize');
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

                }
            })
        });


        $(window).ready(function() {
            // force cleaning pkey value when modal closed
            $('#sendAmountModal').on('hidden.bs.modal', function () {
                $('#pkeyInput').val('');
            });

            // Every time a modal is shown, if it has an autofocus element, focus on it.
            $('.modal').on('shown.bs.modal', function() {
                $(this).find('[autofocus]').focus();
            });

            resizeContainer();
        });
        $scope.$on('windowResizeEvent', resizeContainer);
    }

    angular.module('HarmonyApp')
        .controller('WalletCtrl', ['$scope', '$timeout', '$stomp', '$http', 'jsonrpc', '$q', 'scrollConfig', WalletCtrl])

})();
