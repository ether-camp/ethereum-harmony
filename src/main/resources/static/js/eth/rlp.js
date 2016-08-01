define(function() {
    'use strict';

    var STRING = 0x80,
        LONG_STRING = 0xb8,
        LIST = 0xc0,
        LONG_LIST = 0xf8;

    function getHighlightHTML(input, stringPos, result) {
        if (typeof stringPos !== 'number') {
            stringPos = 0;
        }
        if (typeof result !== 'string') {
            result = '';
        }

        if (!input || stringPos >= input.length) {
            return result;
        }

        var byteHex = input.substr(stringPos, 2), // hex string: item type + length
        b0 = parseInt(byteHex, 16), // int value of byteHex
        lengthOfLength = 0, // length of length of data
        lengthHex = '', // hex string of data length
        dataLen = 0; // length of data

        // step past the first byteHex
        stringPos += 2;

        if (b0 <= STRING) {
            if (byteHex.length > 0) {
                result += '<span class="rlp-byte">' + byteHex + '</span>';
            }
        } else {
            // get the offset to subtract from the first byte
            // this is the type of the following data
            var offset = (b0 < LONG_STRING ?
                STRING : b0 < LIST ?
                LONG_STRING : b0 < LONG_LIST ?
                LIST : LONG_LIST),

            // use different CSS class for string and list lengths
            lenCssClass =
                (offset === STRING || offset === LONG_STRING) ? 'rlp-str-len' : 'rlp-lst-len';

            if (offset === LONG_STRING || offset === LONG_LIST) {
                lengthOfLength = (b0 - (offset - 1)) * 2;
                lengthHex = input.substr(stringPos, lengthOfLength);

                if (offset === LONG_STRING) {
                    dataLen = parseInt(lengthHex, 16) * 2 || 0;
                }

                // step past the length of length byte
                stringPos += lengthOfLength;
            } else {
                dataLen = (b0 - offset) * 2;
            }

            if (byteHex.length + lengthHex.length > 0) {
                result += '<span class="' + lenCssClass + '">' + byteHex + lengthHex + '</span>';
            }

            if (offset !== LONG_LIST) {
                var ll = input.substr(stringPos, dataLen);
                if (ll.length > 0) {
                    result += '<span class="rlp-str">' + ll + '</span>';
                }
            }
        }

        // step past the data
        stringPos += dataLen;

        return getHighlightHTML(input, stringPos, result);
    }

    function highlight(text) {
        text = text || '';
        text.trim().replace(/[^0-9a-f]/gi, '');
        return getHighlightHTML(text);
    }

    return {
        highlight: highlight
    }
});
