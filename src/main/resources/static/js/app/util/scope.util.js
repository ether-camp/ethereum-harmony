/**
 * Utils related to scope.
 */

(function() {
    var module = angular.module("scope-util", []);
    module.factory("scopeUtil", ["$rootScope", scopeUtil]);

    console.log('Console util loaded');

    function scopeUtil($rootScope) {

        /**
         * Safely apply angular digest.
         * It will call $scope.$digest only if digest is not currently run.
         * Otherwise function from parameter will be called immediately.
         *
         * It uses in case when scope changes need to be applied but it's called outside of digest loop
         * (e.g. jQuery mouse down handler).
         *
         * @param {Function} fn functino that need to be called in within digest loop
         */
        function safeApply(fn) {
            var phase = $rootScope.$$phase;
            if (phase == '$apply' || phase == '$digest') {
                if (fn) {
                    fn();
                }
            } else {
                $rootScope.$apply(fn);
            }
        }

        return {
            safeApply: safeApply
        };
    }
}());
