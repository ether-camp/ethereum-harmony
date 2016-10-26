/**
 * Controllers for modal popups.
 *
 */

(function() {
    'use strict';

    /**
     * Runs sha3 (keccak) on phrase multiple times.
     * Note: that Buffer is used inside for sha3
     */
    function generateKeyByPhrase(phrase) {
        return '0x' + applySha3WithBuffer(phrase, 2031).toString('hex');
    }

    function applySha3WithBuffer(data, times) {
        if (times > 0) {
            return applySha3WithBuffer(EthUtil.sha3buffer(data), times - 1)
        } else {
            return data;
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


    function SendAmountCtrl($scope, item, $element, $stomp, close, jsonrpc, $q) {
        $scope.SIGNTYPE_KEYSTORE     = 'SIGNTYPE_KEYSTORE';
        $scope.SIGNTYPE_PRIVATE      = 'SIGNTYPE_PRIVATE';
        //$scope.SIGNTYPE_PHRASE       = 'SIGNTYPE_PHRASE';

        $scope.txData = {
            fromAddress:    item.publicAddress,
            toAddress:      '',
            amount:         '',
            availableAmount: item.amount,
            hasKeystoreKey: item.hasKeystoreKey,
            secret:         '',
            signType:       item.hasKeystoreKey ? $scope.SIGNTYPE_KEYSTORE : $scope.SIGNTYPE_PRIVATE
        };

        var add0x = Utils.Hex.add0x;
        var remove0x = Utils.Hex.remove0x;

        function isAddressesEqual(a1, a2) {
            return remove0x(a1).toLowerCase() == remove0x(a2).toLowerCase();
        }

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

            $scope.form.$setSubmitted();
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
                    var gasLimit = 21000;

                    if (txData.signType == $scope.SIGNTYPE_KEYSTORE) {
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
                        //if (txData.signType == $scope.SIGNTYPE_PHRASE) {
                        //    secret = generateKeyByPhrase(secret);
                        //    //console.log('Phrase generated private key ' + secret);
                        //}

                        var isPrivateKey = (remove0x(secret)).length == 64 && Utils.Hex.isHexString(secret);
                        console.log('isPrivateKey ' + isPrivateKey);
                        var privateKey = isPrivateKey ? ('0x' + remove0x(secret)) : (EthUtil.sha3(secret));
                        var addressFromKey = EthUtil.toAddress(privateKey);
                        console.log('addressFromKey ' + addressFromKey);
                        var isSimpleAddressEquals = isAddressesEqual(txData.fromAddress, addressFromKey);

                        if (isSimpleAddressEquals) {
                            console.log('Using ' + (isPrivateKey ? 'plain key' : 'sha3 single pass'));
                            secret = privateKey;
                        } else {
                            var mnemonicKey = generateKeyByPhrase(secret);
                            var mnemonicAddress = EthUtil.toAddress(mnemonicKey);

                            var isMnemonicAddressEquals = isAddressesEqual(txData.fromAddress, mnemonicAddress);
                            if (isMnemonicAddressEquals) {
                                secret = mnemonicKey;
                            } else {
                                throw new Error('Secret phrase or key doesn\'t match your address.');
                            }
                        }


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
                                //console.log(rlp);

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
                    if (txData.signType == $scope.SIGNTYPE_KEYSTORE) {
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
            name:       ''
        };

        $scope.onImportAddressConfirmed = function() {
            console.log('onImportAddressConfirmed');

            $scope.$broadcast('show-errors-check-validity');

            $scope.form.$setSubmitted();
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

            $scope.form.$setSubmitted();
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

    /**
     * NewAddressCtrl
     */

    function NewAddressCtrl($scope, $stomp, $element, close) {
        $scope.newAddressData = {
            address:    '',
            secret:     ''
        };

        $scope.onNewAddressConfirmed = function() {
            console.log('onImportAddressConfirmed');

            $scope.$broadcast('show-errors-check-validity');

            $scope.form.$setSubmitted();
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

    /**
     * PhraseAddressCtrl
     */

    function PhraseAddressCtrl($scope, $stomp, $element, close, $http) {
        $scope.phraseAddressData = {
            address:    '',
            phrase:     '',
            name:       ''
        };

        $scope.updateAddress = function() {
            var startTime = new Date().getTime();

            var privateKey = generateKeyByPhrase($scope.phraseAddressData.phrase);

            $scope.phraseAddressData.address = EthUtil.toChecksumAddress(EthUtil.toAddress(privateKey));
            //console.log('Generation took ' + (new Date().getTime() - startTime) + ' ms');
        };

        $scope.onGeneratePhrase = function() {
            $http({
                method: 'GET',
                url: '/wallet/generateWords',
                params: {
                    wordsCount: 5
                }
            }).then(function(result) {
                var words = result.data;
                var insertIndex = getRandomInt(0, words.length);
                words.splice(insertIndex, 0, generateWord());   // insert generated word

                function getRandomInt(min, max) {
                    return Math.floor(Math.random() * (max - min + 1)) + min;
                }

                $scope.phraseAddressData.phrase = result.data.join(' ');
                $scope.updateAddress();
            });
        };
        // allow bootstrap animation to pass
        setTimeout(function() {
            $scope.onGeneratePhrase();
        }, 500);

        $scope.onPhraseAddressConfirmed = function() {
            console.log('onPhraseAddressConfirmed');

            $scope.$broadcast('show-errors-check-validity');

            $scope.form.$setSubmitted();
            if (!$scope.form.$valid) {
                showErrorToastr('FORM VALIDATION', 'Please correct form inputted data.');
                return;
            }

            $stomp.send('/app/importAddress', $scope.phraseAddressData);

            $element.modal('hide');
            close(null, 500);
        };
    }

    angular.module('HarmonyApp')
        .controller('PhraseAddressCtrl', ['$scope', '$stomp', '$element', 'close', '$http', PhraseAddressCtrl]);



    /**
     *
     * TOOLS
     *
     */

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
