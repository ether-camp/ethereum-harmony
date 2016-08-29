/**
 * Controllers for modal popups.
 */

(function() {
    'use strict';

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

    function SendAmountCtrl($scope, item, $element, $stomp, close, jsonrpc, $q) {
        $scope.txData = {
            fromAddress:    item.publicAddress,
            toAddress:      '',
            amount:         0,
            useKeystoreKey: item.hasKeystoreKey,
            secret:         ''
        };

        var add0x = Utils.Hex.add0x;
        var remove0x = Utils.Hex.remove0x;

        $scope.onSignAndSend = function() {
            var amount = parseFloat($scope.txData.amount) * Math.pow(10, 18);
            var txData = $scope.txData;
            var secret = txData.secret;

            console.log('Before sign and send amount:' + amount);
            console.log(txData);

            $q.all([
                    jsonrpc.request('eth_gasPrice', []),
                    jsonrpc.request('eth_getTransactionCount', [add0x(txData.fromAddress), 'latest'])
                ])
                .then(function(results) {
                    console.log(results);

                    var gasPrice = parseInt(remove0x(results[0]), 16);
                    var nonce = parseInt(remove0x(results[1]), 16);
                    var gasLimit =  21000;

                    console.log('txData.useKeystoreKey ' + txData.useKeystoreKey)
                    if (txData.useKeystoreKey) {
                        console.log('try to unlock account with ' + [add0x(txData.fromAddress), secret, null]);
                        return jsonrpc.request('personal_unlockAccount', [add0x(txData.fromAddress), secret, null])
                            .then(function(result) {
                                console.log('Account unlocked ' + result);

                                return jsonrpc.request('eth_sendTransactionArgs', [
                                    add0x(txData.fromAddress),
                                    add0x(txData.toAddress),
                                    add0x(gasLimit.toString(16)),
                                    add0x(gasPrice.toString(16)),
                                    add0x(amount.toString(16)),
                                    add0x(''),
                                    add0x(nonce.toString(16))
                                ]);
                            })
                    } else {
                        return RlpBuilder
                            .balanceTransfer(remove0x(txData.toAddress))
                            .from(remove0x(txData.fromAddress))
                            .secretKey(secret)
                            .gasLimit(gasLimit)
                            .gasPrice(gasPrice)
                            .value(amount)
                            .nonce(nonce)
                            .withData('')
                            .format()

                            .then(function (rlp) {
                                console.log('Signed transaction');
                                console.log(rlp);

                                return jsonrpc.request('eth_sendRawTransaction', [rlp]);
                            });
                    }

                })
                .then(function(txHash) {
                    console.log('eth_sendRawTransaction result:' + txHash);

                    $element.modal('hide');
                    close(null, 500);
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
                    console.log('Error sending amount');
                    console.log(error);
                    if (error.hasOwnProperty('message')) {
                        error = error.message;
                    }
                    showErrorToastr('Problem with transfer', error);
                })
                .then(function() {
                    //.always(function() {
                    if (txData.useKeystoreKey) {
                        console.log('Attempt to lock account back');
                        jsonrpc.request('personal_lockAccount', [add0x(txData.fromAddress)]);
                    }
                })
                .catch(function(e) {
                    console.log('Problem locking key after operation')
                });
        };
    }

    angular.module('HarmonyApp')
        .controller('SendAmountCtrl', ['$scope', 'item', '$element', '$stomp', 'close', 'jsonrpc', '$q', SendAmountCtrl]);


    function ImportAddressCtrl($scope, $timeout, $stomp, $http, jsonrpc, $q, scrollConfig, ModalService) {

    }

    angular.module('HarmonyApp')
        .controller('ImportAddressCtrl', ['$scope', '$timeout', '$stomp', '$http', 'jsonrpc', '$q', 'scrollConfig', 'ModalService', ImportAddressCtrl]);

})();
