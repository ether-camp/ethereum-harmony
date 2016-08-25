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
        if (value && value.indexOf('0x' == 0)) {
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

        $scope.onSendClick = function(address) {
            console.log('onSendClick');
            $scope.txData = {
                fromAddress: address.publicAddress,
                toAddress: 'da000c02f99e78b454427c3407cf906938747f58',
                amount: 0
            };
            $('#sendAmountModal').modal({});
        };

        $scope.onSignAndSend = function() {
            var privateKey = $('#pkeyInput').val();
            var txData = $scope.txData;

            console.log('Before sign and send');
            console.log(txData);

            $q.all([
                jsonrpc.request('eth_gasPrice', []),
                jsonrpc.request('eth_getTransactionCount', ['0x' + txData.fromAddress, 'latest'])
            ])
                .then(function(results) {
                    console.log(results);

                    var gasPrice = results[0];
                    var nonce = results[1];
                    var gasLimit = '0x9f759';

                    return;
                    RlpBuilder
                        .balanceTransfer(remove0x(txData.toAddress))    // to address
                        .from(remove0x(txData.fromAddress))
                        .secretKey(privateKey)
                        .gasLimit(gasLimit)
                        .gasPrice(gasPrice)
                        .value(txData.value * 1000, defaultCurrency)
                        .nonce(nonce)
                        //.invokeData(data)
                        .withData('0x0')
                        .format()
                        .done(function (rlp) {
                            console.log('Signed transaction');
                            console.log(rlp);

                            jsonrpc.request('eth_sendRawTransaction', [rlp])
                                .then(function(result) {
                                    //console.log(result);
                                    console.log('eth_sendRawTransaction result:' + result);
                                    terminal.echo(result);
                                    $('#signWithKeyModal').modal('hide');
                                })
                                .catch(function(error) {
                                    console.log('Error sending raw transaction');
                                    console.log(error);
                                    showErrorToastr('ERROR', 'Wasn\'t to send signed raw transaction.\n' + error);
                                });
                        })
                        .fail(function(error) {
                            console.log('Error signing tx ' + error);
                            showErrorToastr('ERROR', 'Wasn\'t able to sign transaction.\n' + error);
                            //$balanceDlg.find('input[name="key"]').addClass('error').focus();
                        })
                        .always(function () {
                            // Hide progress indicator
                            //$progressIndicator.fadeOut(function () {
                            //    $(this).remove();
                            //});
                        });
                });



        };

        // load initial data
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
                    $scope.totalAmount = info.totalAmount;
                    $scope.totalAmountString = numberWithCommas(info.totalAmount / ETH_BASE);
                    info.addresses.forEach(function(a) {
                        a.amount = numberWithCommas(a.amount / ETH_BASE);
                    });
                    $scope.addresses = info.addresses;
                }, 10);

                $http({
                    method: 'GET',
                    url: 'https://coinmarketcap-nexuist.rhcloud.com/api/eth'
                }).then(function(result) {
                    try {
                        var price = result.data.price.usd;
                        $scope.totalAmountUSD = numberWithCommas((info.totalAmount / ETH_BASE) * price);
                    } catch (e) {

                    }
                })
            });
            $stomp.send('/app/getWalletInfo');
        }


        $(window).ready(function() {
            resizeContainer();
        });
        $scope.$on('windowResizeEvent', resizeContainer);
    }

    angular.module('HarmonyApp')
        .controller('WalletCtrl', ['$scope', '$timeout', '$stomp', '$http', 'jsonrpc', '$q', 'scrollConfig', WalletCtrl])

})();
