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
        $scope.signKeystore     = 'signKeystore';
        $scope.signPrivate      = 'signPrivate';
        $scope.signPhrases      = 'signPhrases';

        $scope.txData = {
            fromAddress:    item.publicAddress,
            toAddress:      '',
            amount:         '',
            availableAmount: item.amount,
            hasKeystoreKey: item.hasKeystoreKey,
            secret:         '',
            signType:       item.hasKeystoreKey ? $scope.signKeystore : $scope.signPrivate
        };

        var add0x = Utils.Hex.add0x;
        var remove0x = Utils.Hex.remove0x;

        jsonrpc.request('eth_gasPrice', [])
            .then(function(value) {
                var gasPrice = parseInt(remove0x(value), 16);
                var gasLimit =  21000;

                var gasCost = new BigNumber('' + (gasPrice * gasLimit)).dividedBy(new BigNumber('' + Math.pow(10, 18)));
                $scope.txData.availableAmount = Math.max(0,
                    new BigNumber('' + item.amount).plus( gasCost.neg()).toNumber());
            });

        $scope.onSendAll = function () {
            $scope.txData.amount = $scope.txData.availableAmount;
        };

        $scope.onSignAndSend = function() {
            $scope.$broadcast('show-errors-check-validity');

            if (!$scope.form.$valid) {
                showErrorToastr('FORM VALIDATION', 'Please correct form inputted data.');
                return;
            }

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

                    if (txData.signType == $scope.signKeystore) {
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
                            .balanceTransfer(remove0x(txData.toAddress).toLowerCase())
                            .from(remove0x(txData.fromAddress).toLowerCase())
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
                    if (txData.signType == $scope.signKeystore) {
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


    function ImportAddressCtrl($scope, $stomp, $element, close) {
        $scope.importAddressData = {
            address:    '',
            name:       '',
            type:       'address'
        };

        $scope.onImportAddressConfirmed = function() {
            console.log('onImportAddressConfirmed');

            $scope.$broadcast('show-errors-check-validity');

            if (!$scope.form.$valid) {
                showErrorToastr('FORM VALIDATION', 'Please correct form inputted data.');
                return;
            }

            $stomp.send('/app/importAddress', $scope.importAddressData);

            $element.modal('hide');
            close(null, 500);
        };
    }

    angular.module('HarmonyApp')
        .controller('ImportAddressCtrl', ['$scope', '$stomp', '$element', 'close', ImportAddressCtrl]);


    function NewAddressCtrl($scope, $stomp, $element, close) {
        $scope.newAddressData = {
            address:    '',
            secret:     ''
        };

        $scope.onNewAddressConfirmed = function() {
            console.log('onImportAddressConfirmed');

            $scope.$broadcast('show-errors-check-validity');

            if (!$scope.form.$valid) {
                showErrorToastr('FORM VALIDATION', 'Please correct form inputted data.');
                return;
            }

            $stomp.send('/app/newAddress', $scope.newAddressData);

            $element.modal('hide');
            close(null, 500);
        };
    }

    angular.module('HarmonyApp')
        .controller('NewAddressCtrl', ['$scope', '$stomp', '$element', 'close', NewAddressCtrl]);


    angular.module('HarmonyApp')
        .directive('showErrors', function() {
            return {
                restrict: 'A',
                require:  '^form',
                link: function (scope, el, attrs, formCtrl) {
                    // find the text box element, which has the 'name' attribute
                    var inputEl   = el[0].querySelector("[name]");
                    // convert the native text box element to an angular element
                    var inputNgEl = angular.element(inputEl);
                    // get the name on the text box so we know the property to check
                    // on the form controller
                    var inputName = inputNgEl.attr('name');

                    // only apply the has-error class after the user leaves the text box
                    inputNgEl.bind('blur', function() {
                        el.toggleClass('has-error', formCtrl[inputName].$invalid);
                    })
                }
            }
        });

    angular.module('HarmonyApp').directive('ethaddress', function() {
        return {
            require: 'ngModel',
            link: function(scope, elem, attr, ngModel) {
                //For DOM -> model validation
                ngModel.$parsers.unshift(function(value) {
                    var valid = !value || Utils.Hex.isHexAddress(value);
                    ngModel.$setValidity('ethaddress', valid);
                    return valid ? value : undefined;
                });

                //For model -> DOM validation
                ngModel.$formatters.unshift(function(value) {
                    ngModel.$setValidity('ethaddress', !value || Utils.Hex.isHexAddress(value));
                    return value;
                });
            }
        };
    });

    // validate for correct checksum address
    // https://github.com/ethereum/EIPs/issues/55
    angular.module('HarmonyApp').directive('ethaddresschecksum', function() {
        function hasBothLowerAndUpperCases(value) {
            var str = Utils.Hex.remove0x(value);
            return (/[a-z]/.test(str)) && (/[A-Z]/.test(str));
        }

        function isValid(value) {
            return !value || !Utils.Hex.isHexAddress(value) || !hasBothLowerAndUpperCases(value) || EthUtil.isValidChecksumAddress(value);
        }

        return {
            require: 'ngModel',
            link: function(scope, elem, attr, ngModel) {
                //For DOM -> model validation
                ngModel.$parsers.unshift(function(value) {
                    var valid = isValid(value);
                    ngModel.$setValidity('ethaddresschecksum', valid);
                    return valid ? value : undefined;
                });

                //For model -> DOM validation
                ngModel.$formatters.unshift(function(value) {
                    ngModel.$setValidity('ethaddresschecksum', isValid(value));
                    return value;
                });
            }
        };
    });

})();
