var generateWord = (function () {

    function generate2250Word() {
        var number = Math.floor(1000 + Math.random() * 1000 * 1000 * 1000);
        return genWord(number);
    }

    function genWord(number) {
        var base2250Num = [];
        var base2250Str = [];
        for (var i = number; i > 0; i = Math.floor(i / 2250)) {
            base2250Num.unshift(i % 2250);
            //convert numbers to syllables
            base2250Str.unshift(genSyllable(i % 2250));
        }
        var outputWord = base2250Str[0];

        for (var index = 1; index < base2250Str.length; ++index) {
            outputWord += '' + base2250Str[index];
        }

        return outputWord;
    }

    function genSyllable(n) {
        var consonants = 'bcdfghjklmnprstvwz'; // consonants except hard to speak ones
        var vowels = 'aeiou'; // vowels
        var syllableStr = '';
        var m = 0;

        if (n < 90) {
            // If less than 18*5 then return Consoant + Vowel
            syllableStr = consonants[Math.floor(n / 5) % 18] + vowels[n % 5];
        } else if (n <= 180) {
            // Less than 18*5*2 then return Vowel + Consoant
            m = n - 90;
            syllableStr = vowels[Math.floor(m / 18) % 5] + consonants[m % 18];
        } else if (n <= 630) {
            // Less than 5*18*5 then return Vowel + Consoant + Vowel
            m = n - 180;
            syllableStr = vowels[Math.floor(m / 90) % 5] +
                consonants[Math.floor(m / 5) % 18] + vowels[m % 5];
        } else {
            // Less than 5*18*5 then return Consoant + Vowel + Consoant
            m = n - 630;
            syllableStr = consonants[Math.floor(m / 90) % 18] +
                vowels[Math.floor(m / 18) % 5] + consonants[m % 18];
        }

        return syllableStr;
    }

    return generate2250Word;
})();