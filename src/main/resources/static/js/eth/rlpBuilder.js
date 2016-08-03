//define([
//    'jquery',
//    'underscore',
//    'currency',
//    'utils',
//    'ethUtils',
//    'solFunc'
//], function ($, _, currency, Utils, EthUtils) {

var Utils = (function() {
    function isHexString(string) {
        return /^(0x)?[\da-f]+$/i.test(string);
    }

    function padHexString(h) {
        if (typeof h === 'string' && h.length % 2 !== 0) {
            h = '0' + h;
        }
        return h;
    }

    function toHexString(value) {
        return padHexString((new BigNumber(value)).toString(16));
    }

    return {
        Hex: {
            isHexString: isHexString,
            padHexString: padHexString,
            toHexString: toHexString
        }
    }
})();



var RlpBuilder = (function() {
    function defaultInt(value, defaultValue, isAbs) {
        value || (value = defaultValue);
        _.isString(value) && (value = parseInt(value, 10));
        _.isNaN(value) && (value = defaultValue);

        return isAbs ? Math.abs(value) : value;
    };

    var TxRlpFormatter = function (toAddr, dummy) {

        this.data = {
            to: toAddr.replace('0x', ''),
            data: '',
            dummy: dummy
        };

        _.extend(TxRlpFormatter.prototype, {
            from: from,
            seedPhrase: setSeedPhrase,
            privateKey: setPrivateKey,
            secretKey: setSecretKey,
            value: setValue,
            gasLimit: setGasLimit,
            gasPrice: setGasPrice,
            invokeData: setInvokeData,
            nonce: setNonce,
            format: format,
            formatAndSubmit: formatAndSubmit
        });

        var self = this;

        function format() {
            var dfd = $.Deferred();

            if (self.data.sender && self.data.sender !== self.data.from) {
                console.log(self.data.sender);
                console.log(self.data.from);
                return dfd.reject('Signature is invalid, please check this one.').promise();
            }

            var txData = _.pick(self.data, 'data', 'value', 'gasLimit', 'pkey', 'to', 'gasPrice', 'nonce');

            var dfds = [];
            if (true) {
                //txData.nonce = self.data.nonce;
                //txData.gasPrice = self.data.gasPrice;
                console.log(txData);
            } else if (self.dummy) {
                dfds.push($.Deferred().resolve().done(function () {
                    _.extend(txData, {
                        nonce: -1,
                        gasPrice: 0
                    })
                }));
            } else {
                dfds.push(workspace.state.getAccountNonce(self.data.from).done(function (nonce) {
                    txData.nonce = nonce;
                }));
                dfds.push(workspace.state.getGasPrice().done(function (gasPrice) {
                    txData.gasPrice = gasPrice;
                }));
            }

            $.when.apply($, dfds).done(function () {
                txData.pkey = '0x' + txData.pkey;
                console.log('txData before create tx');
                console.log(txData);
                var rlp = EthUtil.createTx(txData);
                dfd.resolve(rlp.replace('0x', ''));
            }).fail(function () {
                dfd.reject();
            });

            return dfd.promise();
        }

        function formatAndSubmit() {
            var dfd = $.Deferred();
            format().done(function (rlp) {
                $.ajax(workspace.serviceUrl.state + '/transaction/submit', {
                    method: 'POST',
                    data: {
                        rlp: rlp
                    }
                }).done(function (resp) {
                    dfd.resolve(resp);
                }).fail(function () {
                    dfd.reject();
                });
            });

            return dfd.promise();
        }

        function setSeedPhrase(seed) {
            return setPrivateKey(EthUtil.sha3(seed));
        }

        function setPrivateKey(pkey) {
            self.data.pkey = pkey.replace('0x', '');
            self.data.from = EthUtil.toAddress('0x' + self.data.pkey).replace('0x', '');
            console.log("set pkey:" + self.data.pkey);
            console.log("set from:" + self.data.from);
            return self;
        }

        function setSecretKey(pkeyOrSeed) {
            if (_.size(pkeyOrSeed.replace('0x', '')) == 64 && Utils.Hex.isHexString(pkeyOrSeed)) {
                return setPrivateKey(pkeyOrSeed);
            } else {
                return setSeedPhrase(pkeyOrSeed);
            }
        }

        function setValue(value, denomination) {
            value = defaultInt(value, 0);
            if (value && denomination !== 'wei') {
                value = currency.convert(value, denomination, 'wei').toNumber();
            }

            self.data.value = value;
            return self;
        }

        function setGasLimit(limit) {
            self.data.gasLimit = defaultInt(limit, 70000000000);
            return self;
        }

        function setGasPrice(gasPrice) {
            self.data.gasPrice = defaultInt(gasPrice, 21000);
            return self;
        }

        function setNonce(nonce) {
            self.data.nonce = defaultInt(nonce, 0);
            return self;
        }

        function setInvokeData(methodAbi, methodArgs) {
            self.data.data = new SolidityFunction({}, methodAbi, self.data.to).toPayload(methodArgs).data.replace('0x', '');
            return self;
        }

        function from(sender) {
            self.data.sender = sender;
            return self;
        }
    };

    TxRlpFormatter.contractInvoke = function (toAddr, methodAbi, methodArgs) {
        return new TxRlpFormatter(toAddr).invokeData(methodAbi, methodArgs);
    };

    TxRlpFormatter.balanceTransfer = function (toAddr) {
        return new TxRlpFormatter(toAddr);
    };





    return {

        contractInvoke: function (toAddr, methodAbi, methodArgs) {
            return new TxRlpFormatter(toAddr).invokeData(methodAbi, methodArgs);
        },

        balanceTransfer: function (toAddr) {
            return new TxRlpFormatter(toAddr);
        },

        dummy: function () {
            return new TxRlpFormatter('ffffffffffffffffffffffffffffffffffffffff', true)
                .value(0, 'wei')
                .gasLimit(0);
        }
    };
})();
