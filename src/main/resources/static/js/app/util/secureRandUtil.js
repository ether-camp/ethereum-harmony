// Cryptographically secure random functions
var secureRandUtil = (function () {

    var cryptoObj = window.crypto || window.msCrypto; // for IE 11
    var max32 = Math.pow(2, 32);  // max 32 bit integer

    function and(v1, v2) {  // 64-bit bitwise AND
        var hi = 0x80000000;
        var low = 0x7fffffff;
        var hi1 = ~~(v1 / hi);
        var hi2 = ~~(v2 / hi);
        var low1 = v1 & low;
        var low2 = v2 & low;
        var h = hi1 & hi2;
        var l = low1 & low2;
        return h*hi + l;
    }

    function getRandomInt(min, max) {
        var rval = 0;
        var range = max - min;

        var bits_needed = Math.ceil(Math.log(range) / Math.log(2));
        if (bits_needed > 53) {
            throw new Exception("We cannot generate numbers larger than 2^53");
        }
        var bytes_needed = Math.ceil(bits_needed / 8);

        // Create byte array and fill with N random numbers
        var byteArray = new Uint8Array(bytes_needed);
        cryptoObj.getRandomValues(byteArray);

        var p = (bytes_needed - 1) * 8;
        for (var i = 0; i < bytes_needed; i++) {
            rval += byteArray[i] * Math.pow(2, p);
            p -= 8;
        }

        // Use & to apply the mask and reduce the number of recursive lookups
        var mask = Math.pow(2, bits_needed) - 1;
        if (rval > max32 || mask > max32) {
            rval = and(rval, mask);
        } else {
            rval = rval & mask;
        }

        if (rval >= range) {
            // Integer out of acceptable range
            return getRandomInt(min, max);
        }
        // Return an integer that falls within the range
        return min + rval;
    }

    return {
        getRandomInt: getRandomInt
    };
})();