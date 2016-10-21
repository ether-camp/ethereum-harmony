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

    function isHexAddress(string) {
        return /^(0x)?[\da-f]{40}$/i.test(string);
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

    function add0x(value) {
        return '0x' + remove0x(value);
    }

    function remove0x(value) {
        return (value && value.indexOf('0x') == 0) ? value.substr(2) : value;
    }


    return {
        Hex: {
            isHexString:    isHexString,
            padHexString:   padHexString,
            toHexString:    toHexString,
            add0x:          add0x,
            remove0x:       remove0x,
            isHexAddress:   isHexAddress
        },

        Format: {
            /**
             * @example 1000 -> "1,000"
             */
            numberWithCommas: function numberWithCommas(x) {
                var arr = x.toString().split('.');
                var arr1 = arr[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",");
                return arr.length == 1 ? arr1 : arr1 + '.' + arr[1];
            }
        }
    }
})();



var RlpBuilder = (function() {

    var remove0x = Utils.Hex.remove0x;

    function defaultInt(value, defaultValue, isAbs) {
        value || (value = defaultValue);
        _.isString(value) && (value = parseInt(value, 10));
        _.isNaN(value) && (value = defaultValue);

        return isAbs ? Math.abs(value) : value;
    };

    var TxRlpFormatter = function (toAddress, dummy) {

        this.data = {
            to: remove0x(toAddress),
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
            withData: setData,
            nonce: setNonce,
            format: format
        });

        var self = this;

        function format() {
            var dfd = $.Deferred();

            if (self.data.sender && self.data.sender !== self.data.from) {
                console.log(self.data.sender);
                console.log(self.data.from);
                return dfd.reject('Signature and sender address doesn\'t match.').promise();
            }

            var txData = _.pick(self.data, 'data', 'value', 'gasLimit', 'pkey', 'to', 'gasPrice', 'nonce');

            var dfds = [];
            //console.log(txData);

            txData.pkey = '0x' + txData.pkey;
            console.log('txData before create tx');
            //console.log(txData);
            var rlp = EthUtil.createTx(txData);
            dfd.resolve(remove0x(rlp));

            return dfd.promise();
        }

        function setSeedPhrase(seed) {
            return setPrivateKey(EthUtil.sha3(seed));
        }

        function setPrivateKey(pkey) {
            self.data.pkey = remove0x(pkey);
            self.data.from = remove0x(EthUtil.toAddress('0x' + self.data.pkey));
            //console.log("set pkey:" + self.data.pkey);
            //console.log("set from:" + self.data.from);
            return self;
        }

        function setSecretKey(pkeyOrSeed) {
            if (_.size(remove0x(pkeyOrSeed)) == 64 && Utils.Hex.isHexString(pkeyOrSeed)) {
                return setPrivateKey(pkeyOrSeed);
            } else {
                return setSeedPhrase(pkeyOrSeed);
            }
        }

        function setValue(value) {
            value = defaultInt(value, 0);
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
            self.data.data = remove0x(new SolidityFunction({}, methodAbi, self.data.to).toPayload(methodArgs).data);
            return self;
        }

        function setData(value) {
            self.data.data = value;
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

        contractInvoke: function (toAddress, methodAbi, methodArgs) {
            return new TxRlpFormatter(toAddress).invokeData(methodAbi, methodArgs);
        },

        balanceTransfer: function (toAddress) {
            return new TxRlpFormatter(toAddress);
        },

        dummy: function () {
            return new TxRlpFormatter('ffffffffffffffffffffffffffffffffffffffff', true)
                .value(0, 'wei')
                .gasLimit(0);
        }
    };
})();
